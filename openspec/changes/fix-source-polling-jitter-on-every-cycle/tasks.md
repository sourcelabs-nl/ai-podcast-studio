## 1. Scheduler change

- [x] 1.1 Add `firstCycle` flag to `SourcePollingScheduler` and gate `applyStartupJitter` so it runs only on the first call to `pollSources()` after `ApplicationReadyEvent`.
- [x] 1.2 Preserve the existing `applyStartupJitter` implementation so behavior on the first cycle is unchanged.

## 2. Tests

- [x] 2.1 Add unit test `newly added source with null lastPolled is polled on next cycle without jitter` in `SourcePollingSchedulerTest`, asserting that a source added between two `pollSources()` calls is polled on the second call and `sourceRepository.save` is not invoked for it.
- [x] 2.2 Verify existing tests still pass: `mvn test -Dtest='SourcePolling*'`.
