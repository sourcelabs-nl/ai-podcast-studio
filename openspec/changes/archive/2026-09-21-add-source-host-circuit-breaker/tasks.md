## 1. Dedup de-duplication

- [x] 1.1 In `TopicDedupFilter.filter`, track selected article ids while walking clusters and skip an article already added by an earlier cluster; count the discards.
- [x] 1.2 Log a `WARN` naming the number of duplicate selections discarded when the count is non-zero.
- [x] 1.3 Change the `[Dedup] Filter complete` log so its "selected" figure is the distinct article count.
- [x] 1.4 In `LlmPipeline.capForCompose`, `distinctBy` article id before sorting by relevance and taking `app.compose.max-articles`.
- [x] 1.5 Extend `TopicDedupFilterTest` with: an article selected by two clusters returned once with the first cluster's topic/follow-up context; a response naming more articles than there were candidates capped at the candidate count; an unchanged result and ordering for a response with no duplicates.
- [x] 1.6 Extend `LlmPipelineTest` to cover a filtered list containing duplicates producing a distinct compose input.

## 2. Feed fetch preserves HTTP status

- [x] 2.1 Inject `RestClient.Builder` into `RssFeedFetcher` and retrieve the feed body through it, sending `ArticleContentFetcher.USER_AGENT`.
- [x] 2.2 Feed the response stream to `XmlReader(inputStream, contentType)` so Rome keeps charset detection from the `Content-Type` header and the XML declaration.
- [x] 2.3 Verify redirect following still matches the previous `URL`-based behaviour for a feed served behind a 301/302.
- [x] 2.4 Extend `RssFeedFetcherTest` with 403/404/500/429 responses asserting the raised exception type, a non-UTF-8 feed asserting correct decoding, and a malformed body asserting a parse failure.
- [x] 2.5 Extend `PollFailureTest` to assert an `HttpClientErrorException` 403 originating from a feed fetch classifies as permanent.
- [x] 2.6 Confirm `SourcePollerFailureTrackingTest` still passes and add a case asserting an RSS 403 sets `lastFailureType = "permanent"`.

## 3. Host circuit breaker (Resilience4j)

- [x] 3.1 Add `resilience4j-spring-boot4` 2.4.0 and `resilience4j-kotlin`, pinned rather than via the BOM (which omits the spring-boot4 artifact), and confirm the Spring context still loads on Boot 4.1.
- [x] 3.2 Configure `resilience4j.circuitbreaker.configs.source-host` in `application.yaml`: count-based window of 3, 100% failure threshold, 24h open state, 1 permitted half-open call, health indicator on.
- [x] 3.3 Add sealed `PollFailureException.Permanent` / `.Transient` and list them under `record-exceptions` / `ignore-exceptions` so transient failures are ignored rather than counted as successes.
- [x] 3.4 Change `SourcePoller.poll` to return its classified `PollFailure` (null on success) so outcomes can be reported to the breaker.
- [x] 3.5 Add `SourceHostBreaker` wrapping the registry: `pollThroughBreaker` returning `Polled`/`Skipped`, plus `isOpen`/`isTripped` for the API and recovery check.
- [x] 3.6 Rewire `SourcePollingScheduler.pollHostGroup` to poll each source through the breaker, counting skips and emitting one aggregate log line per host per round.
- [x] 3.7 On a poll that succeeds while the breaker is tripped, reset the host's other sources via `SourceService.resetFailureState` (schedulers must not touch repositories directly, per the scheduler rules).
- [x] 3.8 Add `ResilienceLoggingConfig` with `RegistryEventConsumer` beans so on-demand breakers and retries are instrumented exactly once.
- [x] 3.9 Test `SourceHostBreaker` against a real `CircuitBreakerRegistry` (window filling, ignored transients, half-open probe succeeding and failing, host isolation, unparseable URL).
- [x] 3.10 Rewrite the scheduler breaker tests for the new semantics: skipping behind an open breaker, opening mid-round, recovery restoring siblings, failing probe, host isolation.
- [x] 3.11 Remove the superseded `HostBreakerProperties` from `AppProperties`.

## 3b. Retry consolidation

- [x] 3b.1 Configure `resilience4j.retry.configs.external-api` plus `article-scoring`, `topic-dedup` and `inworld-tts` instances.
- [x] 3b.2 Replace the `TopicDedupFilter` attempt loop with `retry.executeSuspendFunction`.
- [x] 3b.3 Replace the `ArticleScoreSummarizer` attempt loop, keeping the per-attempt prompt escalation (a byte-identical retry would be served from `CachingChatModel`).
- [x] 3b.4 Replace the `InworldTtsProvider` attempt loop, expressing its three retryable fault types as `retry-exceptions` in YAML.
- [x] 3b.5 Leave `RoleTagValidationAdvisor` alone and document why: it mutates the prompt per attempt, which `Retry` cannot express, and it implements Spring AI's own `CallAdvisor` extension point.
- [x] 3b.6 Remove the superseded `app.llm.scoring.max-retries` property.
- [x] 3b.7 Add `testRetryRegistry` / `testCircuitBreakerRegistry` test helpers and migrate the affected tests onto them.

## 4. API and dashboard

- [x] 4.1 Add breaker state (open flag, host, sibling count) to the source response DTO in `SourceDtos.kt` and populate it in `SourceMappers.kt` from a service-computed breaker view.
- [x] 4.2 Compute breaker state in `SourceService` where the podcast's sources are already loaded, so the controller stays free of logic.
- [x] 4.3 Surface an open breaker in the frontend source list so a suppressed source is visually distinct from a healthy or an individually-failing one.
- [x] 4.4 Run `npx tsc --noEmit` in `frontend/` (never `npm run build` while the dev server is running).

## 5. Verification

- [x] 5.1 `mvn test` green (1217 tests).
- [x] 5.2 Confirmed live: with the nitter sources made due, 3 polls filled the window, `Circuit breaker 'nitter.net' CLOSED -> OPEN` fired, and 33 of 36 were skipped without a request. Stack-trace lines dropped from ~324 per round to 0, the skipped sources kept their prior state, and the API reported `hostBreakerOpen: true`.
- [x] 5.3 Confirm a healthy host (e.g. `simonwillison.net`, `rss.arxiv.org`) still polls and saves posts normally after the `RestClient` change.
- [x] 5.4 Re-run `/code-review --all` after the Resilience4j rewrite and repeat until clean, per the code review loop. Two rounds: first found a duplicated `extractHost` (K3) and types inline in a component file (A7), both fixed; second pass clean.
