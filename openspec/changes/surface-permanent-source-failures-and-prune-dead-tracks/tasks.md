## 1. Permanent client-error classification

- [x] 1.1 Invert `PollFailure.classifyClientError`: 408 and 429 transient, every other 4xx
      permanent, carrying the status text in the message. Verify with `PollFailureTest` cases for
      402, an unenumerated 4xx (418), 408, and the pre-existing 401/404/429 expectations.
- [x] 1.2 Confirm the auto-disable threshold makes the new classification safe rather than
      hair-trigger. Verified: `maxFailures` defaults to 15, so a source survives about 7 hours of
      consecutive permanent failures at a 30-minute poll interval, and `lastFailureType` is recorded
      from the first failure.

## 2. Record dead SoundCloud tracks

- [x] 2.1 In `PublishingService.rebuildSoundCloudPlaylist`, mark the publications behind the stale
      track ids as `UNPUBLISHED` with a cleared `externalId`. Verify with tests for a rebuild with
      one dead and one live track (only the dead row written, playlist rebuilt from the survivor)
      and a rebuild with no dead tracks (no row written).

## 3. Verification

- [x] 3.1 Run `mvn test` and confirm the full suite passes.
- [x] 3.2 Restart the app and confirm on the next playlist rebuild that the dead-track warnings stop
      recurring and the corrected publications no longer appear as published tracks. Verified on
      2026-09-10: the first rebuild corrected 101 publications and logged its last 101 warnings, and
      an immediate second rebuild logged 0. SoundCloud publications now read 6 PUBLISHED (the tracks
      that still exist) against 106 UNPUBLISHED.
