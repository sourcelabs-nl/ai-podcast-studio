## 1. Automatic server-side quota freeing

- [x] 1.1 Replace `SoundCloudPublisher.planQuotaDeletion` (throws) with `freeQuotaIfNeeded`: read `/me` once, size `secondsToFree = required - uploadSecondsLeft + 120s`, delete this podcast's tracks oldest-first until freed, then `delay(4000)` before upload
- [x] 1.2 Make the publish path `suspend` (`EpisodePublisher.publish`, `SoundCloudPublisher.publish`/`freeQuotaIfNeeded`, `FtpPublisher.publish`, `PublishingService.publish`) and use `delay` instead of `Thread.sleep`
- [x] 1.3 Make `PublishingController.publish` a `suspend` handler; bridge `FtpPublisher.update` → suspend `publish` via `runBlocking`

## 2. Remove the user-consent quota flow

- [x] 2.1 Remove `PublishingService.freeQuotaAndPublish` and the `POST .../publish/{target}/free-and-publish` controller endpoint
- [x] 2.2 Remove `SoundCloudQuotaExceededException`, `QuotaDeletionPlan`, `QuotaTrackToDelete`, and `FreeQuotaRequest`
- [x] 2.3 Remove the HTTP 413 `quota_exceeded` handler from `PublishingExceptionHandler`
- [x] 2.4 Remove the quota-consent step (track list + "Remove & publish") from `publish-wizard.tsx`

## 3. Tests and verification

- [x] 3.1 Update `SoundCloudPublisherTest` for the automatic delete-then-upload flow; use `runTest` for the delay-hitting test to skip virtual time
- [x] 3.2 Update `PublishingServiceTest`, `PublishingControllerTest`, `AutoPublishListenerTest`, `FtpPublisherTest` for `suspend` signatures (MockK `coEvery`/`coVerify`, `runBlocking`/`runTest` bridges, MockMvc `asyncDispatch`)
- [x] 3.3 Remove obsolete `free-and-publish` / 413 tests
- [x] 3.4 Run the publishing test suite green (46 tests) and restart the app

## 4. Documentation

- [x] 4.1 Update the `kotlin-quality` skill (Rule K7): flag `Thread.sleep` in `suspend` functions (use `delay`); allow `runBlocking` as a bridge; drive suspend tests with `runTest`; use `coEvery`/`coVerify` for suspend mocks
