## Why

On 2026-08-24 the daily episode was built from 9 distinct articles across 3 hosts, against a baseline of 22-38 articles across 5-7 hosts. Two independent defects combined to produce it, and neither was visible without reading the logs.

**Nitter went away silently.** All 36 `nitter.net/*/rss` sources have returned HTTP 403 since 2026-08-20 (the instance is decommissioned upstream and now serves empty 200s to browsers, 403 to the app). They represented roughly 60% of daily intake. They are still `enabled = true` and never auto-disabled, for two reasons:

1. `RssFeedFetcher` builds the feed from `XmlReader(URI(url).toURL())`, so an HTTP error surfaces as a bare `java.io.IOException` whose status lives only in the message. `PollFailure.classify` has a `403 -> Permanent` branch, but it is only reachable from `HttpClientErrorException`, so every RSS-source HTTP error falls through to the generic `else -> Transient`. Auto-disable only counts permanent failures, so it can never fire for an RSS source.
2. Even with classification fixed, per-source auto-disable would take about two weeks to trip: exponential backoff pins a failing source at the 24-hour cap, and `max-failures` is 15.

The failure is also structural rather than per-source: one dead host took out 36 sources at once, but the system has no concept of a host, so it produced 36 independent stack traces per round and no single signal.

**The dedup filter can multiply articles.** `TopicDedupFilter` appends one `FilteredArticle` per cluster membership with no de-duplication, so an LLM response that lists the same article ID across several clusters inflates the result. On 2026-08-24 it returned `68 candidates -> 356 selected across 44 clusters` (in 2m51s, versus ~13s on healthy days). `LlmPipeline.capForCompose` then sorted those 356 by relevance and took the top 40, which filled all 40 slots with repeated copies of the highest-scoring handful. The composer received "40 articles" that were 9 distinct ones.

## What Changes

- **New dependency:** `resilience4j-spring-boot4` 2.4.0 (plus `resilience4j-kotlin`), supplying the circuit breaker and retry implementations, YAML-bound configuration and actuator metrics.
- **New:** a host-level circuit breaker, one Resilience4j `CircuitBreaker` per source host shared by all that host's sources. Permanent failures fill its sliding window and open it; transient failures are ignored. While open, the host's sources are skipped without a request, one aggregate log line replaces the per-source stack traces, and the state is exposed on the source API so the UI can show it.
- **New:** recovery through Resilience4j's half-open state. After the configured wait, exactly one probe reaches the host while the rest stay rejected; success closes the breaker, failure reopens it. On recovery the host's other sources have their failure counters cleared so the host resumes at its normal interval rather than its accumulated backoff.
- **Changed:** the four hand-written retry loops in `TopicDedupFilter`, `ArticleScoreSummarizer` and `InworldTtsProvider` are replaced with named Resilience4j `Retry` instances, removing two verbatim copies of the same exponential-backoff expression. `RoleTagValidationAdvisor` is deliberately left as-is: it mutates the prompt with the specific validation error per attempt, which a retry policy cannot express.
- **Changed:** `SourcePoller.poll` returns its classified `PollFailure` (null on success) instead of only recording it, so the breaker can observe outcomes without re-reading the row.
- **Modified:** `RssFeedFetcher` fetches the feed over a `RestClient` so HTTP errors arrive as `HttpClientErrorException`/`HttpServerErrorException`. This makes the existing `403/404/410/401 -> Permanent` classification reachable for RSS and YouTube sources, which is a precondition for both auto-disable and the breaker's permanent/transient distinction.
- **Modified:** `TopicDedupFilter` de-duplicates its returned articles by article id, keeping the first cluster that selects each article. `LlmPipeline.capForCompose` additionally de-duplicates defensively before applying the compose cap, so the composer can never receive the same article twice.
- **Modified:** the dedup filter logs distinct-article counts, and warns when the LLM response selected an article in more than one cluster.

Not breaking, and no database migration: breaker state lives in the Resilience4j registry.

## Capabilities

### New Capabilities

- `source-host-circuit-breaker`: per-host Resilience4j breaker, permanent-vs-transient recording, skip-without-polling behaviour, half-open recovery with sibling restoration, and API exposure of breaker state.
- `external-api-retry`: Resilience4j-backed retry for the LLM and TTS call sites, its configuration, and the behaviours (prompt escalation, advisor-based validation feedback) that must survive the move.

### Modified Capabilities

- `source-polling-backoff`: error classification requirement gains a guarantee that the HTTP status of a feed fetch survives into `PollFailure`, so RSS/YouTube sources classify permanent errors correctly instead of defaulting to transient.
- `article-dedup-filter`: the filter's output requirement gains a guarantee that each article appears at most once in the filtered result, regardless of how many clusters the LLM placed it in.

## Impact

**Code**
- `source/RssFeedFetcher.kt` — feed retrieval moves to `RestClient`; status-bearing exceptions
- `source/PollFailure.kt` — no logic change expected, but its contract is now reachable for RSS
- `source/SourcePoller.kt` — records host failure outcomes
- `scheduler/SourcePollingScheduler.kt` — consults the breaker before polling a host group; canary selection
- `source/SourceHostBreaker.kt` (new) — breaker facade over the registry, plus `PollFailureException`
- `config/ResilienceLoggingConfig.kt` (new) — retry/breaker event logging
- `llm/ArticleScoreSummarizer.kt`, `llm/TopicDedupFilter.kt`, `tts/InworldTtsProvider.kt` — retry loops replaced
- No migration: breaker state lives in the Resilience4j registry, not the database
- `llm/TopicDedupFilter.kt` — de-duplicate selected articles
- `llm/LlmPipeline.kt` — defensive de-duplication in `capForCompose`
- `source/SourceController.kt` / source DTO — expose breaker state
- `frontend/` source management view — surface an open breaker

**Config**
- `resilience4j.circuitbreaker.configs.source-host` — window size, failure threshold, open-state wait, half-open permitted calls, recorded/ignored exceptions
- `resilience4j.retry.configs.external-api` and `resilience4j.retry.instances.{article-scoring,topic-dedup,inworld-tts}`
- Removes `app.llm.scoring.max-retries`, superseded by `resilience4j.retry.instances.article-scoring.max-attempts`

**Operational**
- Once deployed, the 36 nitter sources trip the breaker on their next poll round and stop being polled. They remain `enabled` so the user can decide whether to delete them or repoint them at the `TWITTER` source type.
