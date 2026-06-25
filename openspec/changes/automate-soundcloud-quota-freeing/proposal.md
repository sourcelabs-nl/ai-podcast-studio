## Why

When a user's SoundCloud upload quota was full, publishing failed with an HTTP 413 that asked the user to manually approve deleting old tracks (the "free-and-publish" consent flow). That flow was broken: after deleting tracks it immediately re-read the quota from SoundCloud, which still reported the *stale* (pre-deletion) value, so it threw the quota error again and the episode never published. Automated publishing (auto-publish-on-generate) had no quota handling at all and simply failed. The publish wizard and the Publications-tab republish also handled quota differently, so the experience was inconsistent.

## What Changes

- SoundCloud quota freeing is now **fully automatic and server-side**. `SoundCloudPublisher.publish()` reads the quota once (accurate, before any deletion), and if the episode will not fit it deletes the **oldest** tracks for that podcast, oldest-first, freeing just enough (the duration short-fall plus a 120s buffer). Newer tracks and other podcasts' tracks are kept.
- After deleting, the publisher **waits ~4s** before uploading so SoundCloud registers the freed space. The quota is **not** re-read after deletion (that was the source of the stale-quota bug) — deletion is sized from the accurate pre-deletion read.
- The publish path is now `suspend` so the wait uses `delay(...)` instead of `Thread.sleep(...)`: `EpisodePublisher.publish`, `SoundCloudPublisher.publish`/`freeQuotaIfNeeded`, `FtpPublisher.publish`, `PublishingService.publish`, and the `PublishingController.publish` handler.
- **BREAKING** (internal API): the user-consent quota flow is removed — the `POST .../publish/{target}/free-and-publish` endpoint, `PublishingService.freeQuotaAndPublish`, the `SoundCloudQuotaExceededException` / `QuotaDeletionPlan` / `QuotaTrackToDelete` types, the `FreeQuotaRequest` DTO, and the HTTP 413 `quota_exceeded` exception handler.
- The `PublishWizard` no longer has a quota-consent step; publishing now just succeeds (or shows a generic/auth error). Manual publish, republish, and auto-publish-on-generate all flow through the same automatic path and behave identically.

## Capabilities

### New Capabilities
<!-- none -->

### Modified Capabilities
- `soundcloud-integration`: the "SoundCloud upload quota check" requirement changes from a pre-flight check that throws `SoundCloudQuotaExceededException` (HTTP 413 with a deletion plan for user consent) to automatic server-side freeing (delete oldest-first just enough, wait, then upload). The "Frontend re-authorize and quota recovery" requirement drops its HTTP 413 / "Remove Track" quota-consent branch.

## Impact

- Backend: `SoundCloudPublisher`, `PublishingService`, `PublishingController`, `PublishingExceptionHandler`, `PublishingTypes`, `PublishingDtos`, `EpisodePublisher`, `FtpPublisher` (publish path made `suspend`).
- Frontend: `publish-wizard.tsx` (quota-consent step removed).
- API: removes `POST .../publish/{target}/free-and-publish` and the HTTP 413 `quota_exceeded` response from `POST .../publish/{target}`.
- Tests: publishing tests updated for the automatic flow and `suspend` signatures (MockK `coEvery`/`coVerify`, `runTest`/`runBlocking` bridges).
