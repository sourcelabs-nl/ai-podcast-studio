# Tasks

## 1. Source access

- [x] 1.1 Add `findEnabled`, `findEnabledByPodcastId` and the jitter save to `SourceService` with derived queries, and verify with a `SourceService` unit test per method
- [x] 1.2 Switch `SourcePollingScheduler` to those methods, remove `SourceRepository` from its constructor, and verify the scheduler tests pass
- [x] 1.3 Move `isDue` and `effectivePollIntervalMinutes` onto `Source`, and verify with unit tests on `Source` covering a due source, a not-due source and the effective interval

## 2. File hygiene

- [x] 2.1 Move `ResearchTestResult` to `ResearchDtos.kt` and `GenerationStatsRetry` to the llm types file, and verify the project compiles and tests pass
- [x] 2.2 Replace `findById(...).orElse(null)` with `findByIdOrNull(...)` in `FtpPublisher`, `SseEventBroadcaster` and `StaticFeedExporter`, and verify with `mvn test`

## 3. Completion

- [x] 3.1 Run `mvn test`, restart the app, and re-run `/code-review --all` until this batch's findings are gone
