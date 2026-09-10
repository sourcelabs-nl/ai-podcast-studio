<!-- Implemented before this change was written; every task below is already done. -->

## 1. Keep the record out of reach of the side effects

- [x] 1.1 Give `publisher.publish()` its own `try` in `PublishingService.publish()`, returning the `PublishResult`
- [x] 1.2 Save the `PUBLISHED` record immediately after the upload returns, clearing any stale `error_message`
- [x] 1.3 Move the `postPublish` hook, the playlist rebuild, the feed export and the `episode.published` event outside that block
- [x] 1.4 Add `runPostPublishSideEffects` and `runSideEffect`, guarding each step and logging the one that failed
- [x] 1.5 Restructure `updateExisting()` the same way, keeping its `UnsupportedOperationException` path intact
- [x] 1.6 Document in the KDoc why a side effect may not overrule the upload, naming episode 202

## 2. Stop treating cancellation as a failure

- [x] 2.1 Catch `CancellationException` in `publish()` before the general catch, log a warning and rethrow without saving
- [x] 2.2 Do the same in `updateExisting()`
- [x] 2.3 Rethrow `CancellationException` from `runSideEffect` rather than swallowing it

## 3. Tests

- [x] 3.1 `PublishingServiceTest`: a failing `postPublish` hook leaves the record `PUBLISHED` with its external id
- [x] 3.2 `PublishingServiceTest`: a failing feed export leaves the record `PUBLISHED`
- [x] 3.3 `PublishingServiceTest`: a cancelled upload writes no `FAILED` status and leaves the `PENDING` claim
- [x] 3.4 `PublishingServiceTest`: a failing side effect after an update keeps the new external id and stores no error message
- [x] 3.5 `PublishingServiceTest`: the `episode.published` event still fires when a side effect fails, and no `episode.publish.failed` event is emitted
- [x] 3.6 `mvn test` green (1400 tests)

## 4. Repair the damaged data

- [x] 4.1 Restore episode 202's SoundCloud row to `PUBLISHED` with `external_id=2397318972` (asked and approved)
- [x] 4.2 Publish episode 206 to SoundCloud through the API and confirm the same-day replacement removed the old track
- [ ] 4.3 Decide what to do with the orphaned FTP file `briefing-20260909-140250.mp3`
