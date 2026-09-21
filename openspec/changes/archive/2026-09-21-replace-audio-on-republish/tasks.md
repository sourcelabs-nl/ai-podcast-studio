<!-- Implemented before this change was written; every task below is already done. -->

## 1. Replace the audio on a SoundCloud republish

- [x] 1.1 Rewrite `SoundCloudPublisher.update()` to delete the existing track and then delegate to `publish()`, returning the new track id
- [x] 1.2 Insert the `QUOTA_SETTLE_MILLIS` pause between the delete and the upload
- [x] 1.3 Log the deletion and the replacement, naming both the old and the new track id
- [x] 1.4 Document why deleting first is required (permalink reservation, quota) and why the pause stops `freeQuotaIfNeeded` deleting older episodes needlessly
- [x] 1.5 Confirm no local 404 handling is needed, since `SoundCloudClient.deleteTrack` already treats an already-deleted track as done, and record that in the KDoc as what makes a retry safe

## 2. Persist the external id an update returns

- [x] 2.1 Add `externalId = result.externalId` to the `existing.copy(...)` in `PublishingService.updateExisting()`
- [x] 2.2 Log the new id rather than the one passed in
- [x] 2.3 Comment why an update may change external identity, covering both the SoundCloud track id and the FTP filename-derived slug

## 3. Tests

- [x] 3.1 `SoundCloudPublisherTest`: replace the two tests asserting the description-only behaviour
- [x] 3.2 `SoundCloudPublisherTest`: `update` deletes before uploading (`verifyOrder`) and never calls `updateTrack`
- [x] 3.3 `SoundCloudPublisherTest`: `update` returns the new track id, not the one passed in
- [x] 3.4 `SoundCloudPublisherTest`: the replacement upload requests the episode's canonical permalink and title
- [x] 3.5 `SoundCloudPublisherTest`: show notes reach the replacement upload's description
- [x] 3.6 `SoundCloudPublisherTest`: the description falls back to the recap when there are no show notes
- [x] 3.7 `PublishingServiceTest`: an update returning a different external id persists it
- [x] 3.8 Run `mvn test` and confirm the whole suite passes (1179 tests, 0 failures)

## 4. Verification against the running application

- [x] 4.1 Restart and republish episode 184 to FTP only, confirming `external_id` moves from `ftp:briefing-20260821-131633` to `ftp:briefing-20260821-153914` and matches the episode's audio file
- [x] 4.2 Confirm the SoundCloud row was left untouched by the FTP-only republish
- [ ] 4.3 The SoundCloud replacement path is not exercised live by this change: episode 184 was already repaired by hand before the fix existed, and re-running it would delete and re-upload a correct track for no benefit. Covered by unit tests only.
- [ ] 4.4 Episode 141 still holds a stale FTP `external_id` from a June regeneration; left to self-heal on its next publish rather than re-uploading a two-month-old MP3
