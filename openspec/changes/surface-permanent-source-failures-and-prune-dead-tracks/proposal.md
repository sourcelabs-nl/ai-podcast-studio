## Why

Two findings from the 2026-09-10 log review, each a case of a guard listing symptoms instead of
covering its class.

- **A lapsed subscription retried silently for hours.** `PollFailure.classifyClientError` enumerated
  401, 403, 404 and 410 as permanent and defaulted everything else to transient, so the `402 Payment
  Required` returned by the Narro feeds when the trial ended fell through to transient. A transient
  failure never marks a source as needing attention, so the podcast lost all its X content between
  06:03 and the moment a human noticed, with nothing in the UI to say why.
- **Dead SoundCloud tracks were re-requested forever.** Freeing upload quota deletes this podcast's
  oldest tracks but leaves their `episode_publications` rows `PUBLISHED` with the dead track id.
  Every playlist rebuild then asked SoundCloud about each one: 101 dead ids produced 1,180 warnings
  over eight days, which is 65% of all WARN/ERROR lines in that window. The noise is the real cost,
  because it is what a genuine failure has to be spotted among.

## What Changes

- **A client error is permanent unless its status asks for a retry.** `408` and `429` stay transient;
  every other 4xx, enumerated or not, is permanent. A permanent failure records
  `lastFailureType = "permanent"` immediately, which is the signal the UI already surfaces, and
  counts towards the existing auto-disable threshold (15 consecutive failures, so roughly 7 hours at
  a 30-minute interval).
- **A 404 on a track is recorded, not just skipped.** A playlist rebuild marks the publications whose
  tracks no longer exist as `UNPUBLISHED` with a cleared `externalId`. This is both true (the episode
  is genuinely not on SoundCloud any more) and self-healing: the existing 101 dead ids are corrected
  on the next rebuild and never asked about again, and the user can republish an episode if they
  want it back.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `source-polling-backoff`: error classification defaults to permanent for client errors.
- `soundcloud-integration`: a rebuild records the publications whose tracks are gone.

## Impact

- `src/main/kotlin/com/aisummarypodcast/source/PollFailure.kt`
- `src/main/kotlin/com/aisummarypodcast/publishing/PublishingService.kt`
- No migration. No schema change: existing columns are corrected to match reality.
- Behaviour change worth naming: a source returning an unenumerated 4xx now counts towards
  auto-disable where before it retried indefinitely. That is the point, but it means a source can be
  disabled after a sustained client error and needs re-enabling once the cause is fixed.
- Episodes whose SoundCloud track was deleted to free quota will show as unpublished on SoundCloud
  rather than published-with-a-broken-link. This corrects the display; it does not remove anything.
