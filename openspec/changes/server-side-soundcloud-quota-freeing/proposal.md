## Why

When SoundCloud's upload quota was full, the publish wizard orchestrated the recovery entirely in the browser: on a 413 it deleted the single oldest track, then retried publishing on an 8s/15s/30s timer. This was wrong on two counts. (1) It ran multi-step backend orchestration (delete → wait → retry) in the client, which the server should own (architecture Rule A8). (2) It deleted only one track and had no knowledge of the new episode's duration, so it typically freed just the current overage and left zero headroom; the pre-flight guard then blocked every retry at `uploadSecondsLeft <= 0`. In production this meant the auto-retry never succeeded on its own and the user had to manually delete a second track.

## What Changes

- Move all quota arithmetic and track selection to the server. The pre-flight check in `SoundCloudPublisher.publish` now compares the live `uploadSecondsLeft` against the episode's `durationSeconds` (with a safety buffer) instead of just `<= 0`, and computes a **deletion plan**: the oldest podcast tracks to remove so the new episode fits.
- When quota is short, the publish endpoint surfaces the plan as a 413 (`code: quota_exceeded`, `secondsToFree`, `tracksToDelete[]`) for the user to consent to.
- Add `POST .../publish/{target}/free-and-publish` taking the consented `trackIds`. `PublishingService.freeQuotaAndPublish` deletes those tracks server-side and publishes in one operation; a still-insufficient quota re-throws with a fresh plan.
- Introduce `PublishingExceptionHandler` (`@RestControllerAdvice` scoped to `PublishingController`) to translate `SoundCloudQuotaExceededException` (and the other publishing failures) to HTTP, removing the inline try/catch and the client-side retry loop (spring-boot Rule SB8).
- The publish wizard now makes one call, shows the server-computed plan for one-time consent, and calls `free-and-publish`. All client retry/delete/timer logic is removed.

This supersedes the client-side approach documented in `automate-soundcloud-quota-retry`.

## Capabilities

### Modified Capabilities
- `soundcloud-integration`: Quota handling is duration-aware and computes a multi-track deletion plan server-side; a new endpoint deletes the consented tracks and publishes atomically.
- `frontend-publish-wizard`: The wizard forwards the user's consent to a single server endpoint instead of orchestrating delete-and-retry itself.

## Impact

- **Backend**: `SoundCloudPublisher` (duration-aware quota check + `planQuotaDeletion` + `deleteTracks`), `SoundCloudQuotaExceededException` now carries a `QuotaDeletionPlan` (in `PublishingTypes.kt`), `PublishingService.freeQuotaAndPublish`, `PublishingController` `free-and-publish` endpoint, new `PublishingExceptionHandler`.
- **Frontend**: `publish-wizard.tsx` rewritten to a single consent call; retry timers removed.
- **APIs**: publish 413 body changes from `oldestTrack` to `secondsToFree` + `tracksToDelete[]`; new `POST .../publish/{target}/free-and-publish`.
- **Dependencies**: None new.
