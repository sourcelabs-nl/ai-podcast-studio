## Context

`SourcePollingScheduler` already groups due sources by host (`hostGroups = dueSources.groupBy { extractHost(it.url) }`) so that per-host poll delays can be applied serially. That grouping is the natural seam for a breaker: the scheduler knows the host, knows every source under it, and decides what to poll.

Failure state is already persisted per source: `Source.consecutiveFailures`, `Source.lastFailureType` (`"transient"` / `"permanent"`), reset to `0` / `null` on every successful poll by `SourcePoller.poll`. `PollDelayResolver` and `AppProperties.source.hostOverrides` establish that host is already a first-class configuration key.

The blocking defect is upstream of all of this. `RssFeedFetcher.fetch` builds the feed with `SyndFeedInput().build(XmlReader(URI(url).toURL()))`. `java.net.URL` throws a bare `java.io.IOException("Server returned HTTP response code: 403 for URL: ...")` on an HTTP error, so `PollFailure.classify` never sees an `HttpClientErrorException` and falls through to `else -> Transient`. Every RSS and YouTube source therefore reports transient no matter what the server said, which disables both auto-disable and any permanent/transient distinction a breaker would rely on.

## Goals / Non-Goals

**Goals:**
- One aggregate signal when a host is structurally down, instead of N independent per-source failures.
- Use a battle-tested resilience library rather than hand-written breaker and retry logic, and consolidate the existing duplicated retry loops onto it.
- Stop wasting poll rounds (and log volume) on a host that is known to be dead, while still detecting recovery automatically.
- Make the HTTP status of a feed fetch survive into `PollFailure` so the existing classification contract is actually honoured for RSS/YouTube sources.
- Guarantee the composer never receives the same article twice.

**Non-Goals:**
- Replacing or reworking per-source exponential backoff. The breaker sits on top of it.
- Auto-deleting or auto-disabling sources behind an open breaker. The breaker suppresses polling; it does not change `enabled`. Deciding a host is permanently gone stays a human call.
- Tripping on host-wide *transient* outages. Those are what backoff is for (see Decision 3).
- Anything about self-hosted Nitter. That is separate work.

## Decisions

### Decision 1: Resilience4j, not a bespoke breaker

The breaker is `io.github.resilience4j:resilience4j-spring-boot4:2.4.0`, one `CircuitBreaker` per host obtained from the auto-configured `CircuitBreakerRegistry`. Version is pinned rather than taken from `resilience4j-bom`, which omits the `spring-boot4` artifact ([resilience4j#2427](https://github.com/resilience4j/resilience4j/issues/2427)) and so fails to resolve.

Circuit breaking is solved infrastructure with a well-tested state machine, and the parts that look easy to hand-write (window accounting, the half-open transition, thread-safe state) are exactly the parts that are subtle to get right. Configuration lives in `application.yaml` under `resilience4j.circuitbreaker.configs.source-host`, so thresholds are operational settings rather than code, and breaker state and call metrics are exposed through actuator for free.

Call sites use the programmatic `resilience4j-kotlin` API (`executeSuspendFunction`) rather than the `@CircuitBreaker` / `@Retry` AOP annotations, which do not compose with Kotlin `suspend` functions. Spring Framework 7 ships `@Retryable` and `@ConcurrencyLimit` in `org.springframework.resilience`, but no circuit breaker, so the framework's own core resilience support does not cover this case.

*Trade-off:* breaker state lives in the registry, so it resets to CLOSED on restart. The cost is one wasted poll round per restart, after which the window refills and the breaker reopens. That is cheaper than maintaining a second, hand-written state machine to save 36 requests per deploy.

### Decision 2: The window is per host, so it fills in one round

All of a host's sources share one breaker, so a count-based window of 3 fills within a single poll round rather than over days. Without that sharing, a source polled once a day would take three days to open, and per-source state would tell us nothing new over the failure counters we already persist.

`SourcePoller.poll` returns its classified `PollFailure` instead of only recording it, so the breaker can see the outcome. It still handles failures internally; the return value is what lets the caller distinguish a structurally dead host from a slow one without re-reading the row.

### Decision 3: Recovery is the half-open state

`permitted-number-of-calls-in-half-open-state: 1` with `wait-duration-in-open-state: 24h` is the canary: after a day, exactly one call reaches the host while the rest are still rejected, and the breaker closes or reopens on the result. This is the library's own mechanism rather than a probe schedule of our own.

One piece stays ours: when a poll succeeds while the breaker is not closed, the *other* sources on that host have their `consecutiveFailures` cleared. Resilience4j knows the host recovered but not that this application keeps a separate per-source backoff; without the reset the breaker would close while every sibling still sat on its accumulated 24h backoff, and a recovered host would trickle back over days.

### Decision 3b: Permanent failures are recorded, transient ones ignored

`PollFailureException` is sealed into `Permanent` and `Transient` subclasses so `application.yaml` can list one under `record-exceptions` and the other under `ignore-exceptions`.

The distinction matters and is easy to get wrong: an *ignored* exception counts as neither success nor failure, whereas one that is merely not recorded counts as a **success**. Using only a `recordFailurePredicate` would therefore let a stream of timeouts close a breaker on a host that never actually answered.

A host answering 403/404/410 everywhere is gone; one timing out everywhere is having a bad afternoon, which per-source exponential backoff already covers.

### Decision 4: `RssFeedFetcher` fetches over `RestClient`

Feed retrieval moves from `XmlReader(URI(url).toURL())` to an injected `RestClient.Builder` (the pattern already used by `TavilyClient`, `InworldApiClient`, `ElevenLabsApiClient`), which raises `HttpClientErrorException` / `HttpServerErrorException` carrying the status. The response body is handed to `XmlReader(inputStream, contentType)` so Rome keeps doing charset detection from the `Content-Type` header and the XML declaration, exactly as it does today.

The request also gains an explicit `User-Agent` (`ArticleContentFetcher.USER_AGENT`, `"AiSummaryPodcast/1.0"`). Some feeds reject the default Java agent outright, and the deep-fetch path already identifies itself this way.

Redirects must be re-enabled explicitly. The JDK HTTP client behind `RestClient` defaults to *not* following them, whereas the `HttpURLConnection` this fetcher used to go through did; without `followRedirects(NORMAL)` every feed behind a 301 fails on an empty body. `NORMAL` preserves the old refusal to follow an HTTPS→HTTP downgrade.

*Risk:* this is the one change that touches every healthy feed, not just broken ones. Covered under Risks below.

### Decision 6: Existing retry loops move onto Resilience4j Retry

`TopicDedupFilter`, `ArticleScoreSummarizer` and `InworldTtsProvider` each carried their own attempt loop, and the first two duplicated the same backoff expression (`1000L * (1 shl (attempt - 1))`) verbatim. All three now use a named `Retry` from the registry, configured under `resilience4j.retry.instances` from a shared `external-api` base config.

Two behaviours are preserved deliberately:
- `ArticleScoreSummarizer` still tracks the attempt number in a closure, because each retry escalates the prompt with a "raw JSON only" correction and must never re-send a byte-identical prompt (`CachingChatModel` keys on prompt text, so an identical retry would replay the unparseable cached answer).
- `InworldTtsProvider`'s retry set is expressed as `retry-exceptions` in YAML, so only a rate limit, an I/O failure or an upstream 5xx is retried, exactly as before.

`RoleTagValidationAdvisor` is deliberately **not** converted. It mutates the request with the specific validation error before each attempt, which `Retry` cannot express, and it is already an implementation of Spring AI's own `CallAdvisor` extension point for that pattern.

Retry and breaker events are logged through `RegistryEventConsumer` beans in `ResilienceLoggingConfig`, so a breaker created on demand (one per host) is instrumented exactly once however many sources share it.

### Decision 5: De-duplicate in the filter, and again before the cap

`TopicDedupFilter` de-duplicates by article id as it walks clusters, keeping the **first** cluster that selects a given article, so the follow-up annotation and topic label come from the cluster the LLM considered most relevant (they are emitted in the model's own ordering). A `WARN` is logged when any article was selected more than once, since that indicates a degenerating dedup response worth noticing.

`LlmPipeline.capForCompose` de-duplicates again by article id before sorting and taking the cap. This is belt-and-braces: it is one `distinctBy`, and it is the last gate before the composer, so it should not depend on an upstream component behaving.

The existing log line stays but now reports distinct counts, so `68 candidates -> 356 selected` becomes impossible to produce.

## Risks / Trade-offs

- **Moving feed retrieval to `RestClient` regresses a currently-working feed** (gzip handling, redirect following, charset edge case, a server that dislikes the new User-Agent) → the change is behaviour-preserving by construction (same `XmlReader` doing the same detection, just over a different transport), and is covered by tests over the existing fixture feeds. Rollout is observable: a regression shows up as a poll failure on a previously-healthy source within one round, and `RestClient` follows redirects by default like `URL` did.

- **A breaker opens on a host that was only briefly returning 403** (e.g. an aggressive WAF during a traffic spike) → requires filling a window of permanent failures, and the half-open probe re-tests the host every 24h and closes it automatically. Worst case is delayed polling, never lost sources: `enabled` is untouched.

- **Breaker state is lost on restart** → accepted, and the reason a bespoke persisted breaker was rejected. After a restart the host is polled once more, the window refills from the same permanent failures, and the breaker reopens within a single round.

- **The recovery reset masks a genuinely mixed host** — one live source and 35 dead accounts would close the breaker and reset all 36 counters, letting the 35 dead ones climb back to their 24h backoff → accepted. It costs one wasted round per recovery and the counters simply re-accumulate. The alternative (only resetting sources that individually succeed) reproduces the slow-trickle recovery this exists to avoid.

- **A host with a single source now gets a breaker too**, where the earlier hand-written design excluded them → harmless. `minimum-number-of-calls` still requires a full window before it can open, and per-source auto-disable (now reachable thanks to Decision 4) remains the primary mechanism there.

- **A new dependency on a library whose Spring Boot 4 support is one release old** (`resilience4j-spring-boot4` 2.4.0 is the only published version) → the context-load test covers the binding, and the BOM defect is worked around by pinning the version explicitly.

## Migration Plan

No schema migration: breaker state lives in the Resilience4j registry, not the database.

Deployment is a restart. On the first poll round after deploy the 36 nitter sources fail with a now-correctly-classified permanent 403, and once each has accumulated `open-after-failures` such failures the breaker opens and they stop being polled. They remain `enabled` and visible in the UI with their failure state, pending the user's decision to repoint them at a self-hosted instance or delete them.

Rollback means reverting the commit: none of these changes are feature-flagged. A breaker can be disabled at runtime for one host by transitioning it via actuator, but the intended lever is the configuration under `resilience4j.circuitbreaker.configs.source-host`.

## Open Questions

None blocking. Whether the 36 nitter sources are repointed at a self-hosted instance or deleted is a separate decision that this change deliberately does not force.
