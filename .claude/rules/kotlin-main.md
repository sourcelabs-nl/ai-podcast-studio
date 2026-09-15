---
paths:
  - "src/main/kotlin/**/*.kt"
---

# Kotlin main-source authoring rules

**Concurrency**: use Kotlin coroutines for async and background work, never `ExecutorService` or `java.util.concurrent` thread pools directly. Use `Dispatchers.IO` for I/O-bound scopes (HTTP, database, file I/O, TTS), never `Dispatchers.Default`, which is sized to CPU cores and meant for computation.

A long-lived scope holding several independent runs carries a `SupervisorJob`, so one failure cannot cancel its siblings (`PodcastService.pipelineScope` is the pattern). That does not remove the need to catch inside each `launch`: an exception escaping a `launch` reaches the default handler, which is enough to fail an unrelated coroutine test collecting uncaught exceptions. Rethrow `CancellationException` before any general catch, since cancellation is the caller unwinding and not a failure of the work.

**Transactions**: any function performing multiple writes across tables, or multiple writes that must be atomic, is annotated `@Transactional`. It only works on public methods reached through the Spring proxy, so it has no effect on a private method or an internal self-call.

**Parameter objects**: when a parameter list passes 4-5 entries and the parameters are functionally related, wrap them in a data class rather than adding positional arguments. `InworldApiClient.synthesizeSpeech(...)` is the pattern: mandatory request identity as parameters, optional knobs in an `InworldSynthesisOptions` data class, so extending the options does not ripple through every caller.

**Jackson**: this project is on Jackson 3.x (`tools.jackson.*`). Inject the Spring-managed `JsonMapper` bean where a mapper is needed (for example `BeanOutputConverter` in Spring AI); never construct one. Features are configured under `spring.jackson.*` in `application.yaml`, not programmatically.

**Named data classes**: return a named data class, never `Pair`/`Triple`, for multi-value results. Do not define DTOs, mappers or helper types inside a controller or service file; put them in `{Domain}Dtos.kt`, `{Domain}Mappers.kt`, `{Domain}Types.kt`.

Full rules: `architecture` (A6, A7), `kotlin-quality`, and `spring-boot` (SB6) skills.
