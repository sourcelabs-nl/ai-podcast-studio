# Tasks

## 1. Gate

- [x] 1.1 Add `Source.nextPollAt` and use it in `isDue`, and verify with `SourceTest` cases for a never-polled source, a backed-off interval and the backoff cap
- [x] 1.2 Make `BriefingGenerationScheduler.isWindowCovered` defer only for sources that poll again before the deadline, and verify with scheduler tests for a source in backoff, a source that retries inside the deadline and a mix of both

## 2. Completion

- [x] 2.1 Run `mvn test` and restart the app
