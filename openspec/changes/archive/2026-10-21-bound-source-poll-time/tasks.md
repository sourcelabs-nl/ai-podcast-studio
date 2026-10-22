# Tasks

## 1. Feed timeouts

- [x] 1.1 Add `feedConnectTimeout` and `feedReadTimeout` to `AppProperties.SourceProperties` and `application.yaml`, and set them on the client in `RssFeedFetcher`. Verify with `RssFeedFetcherTest` `feed that never responds fails on the read timeout as transient`.

## 2. Poll timeout

- [x] 2.1 Add `pollTimeout` and bound each source's poll in `SourcePollingScheduler` with `withTimeoutOrNull` and `runInterruptible`, logging a timeout. Verify with `SourcePollingSchedulerTest` `a hanging poll is interrupted after the poll timeout and does not hold up the round`.

## 3. Verification

- [x] 3.1 Both new tests failed before the fix and pass after; `mvn test` passes.
