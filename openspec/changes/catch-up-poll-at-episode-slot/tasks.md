# Tasks

## 1. Per-source poll guard

- [x] 1.1 Add a per-source `Mutex` map to `SourcePollingScheduler` and take it around each source poll in `pollHostGroup`; after acquiring, re-read the source through `SourceService.findById` and skip it when it was polled after the caller's request time (catch-up) or is no longer due (loop). Verify with a `SourcePollingSchedulerTest` case that two concurrent polls of the same source result in one `SourcePoller.poll` call.
- [x] 1.2 Add `pollSourcesNow(podcastId, sources)` that polls the given sources through the same host-grouped path, and make `pollPodcastSourcesNow` delegate to it. Verify existing stale catch-up tests still pass and a new test asserts only the given sources are polled.

## 2. Catch-up at the slot

- [x] 2.1 In `BriefingGenerationScheduler`, after the retry gate and `ensureFreshPolling` and before `isWindowCovered`, when the deadline has not passed, poll the behind sources with `consecutiveFailures == 0` via `pollSourcesNow`, logging how many were caught up; errors are logged and generation falls back to the existing coverage check. Verify with `BriefingGenerationSchedulerTest` cases: behind healthy sources are polled and generation runs in the same tick; a failing source is not polled; after the deadline no catch-up runs; nothing behind means no catch-up call.
- [x] 2.2 Run `mvn test`; all tests pass.

## 3. Live check

- [ ] 3.1 Restart the app (`./stop.sh && ./start.sh`) and, at the next slot of a podcast, confirm in `app.log` that the catch-up poll runs right after "is due" and generation starts within a few minutes instead of waiting for regular polls.
