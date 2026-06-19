## Context

The publishing pipeline already exists end-to-end: `PublishingService.publish(episode, podcast, userId, target)` uploads to FTP or SoundCloud, records an `EpisodePublication`, exports the static feed, and emits `episode.published` / `episode.publish.failed` events. Per-podcast targets are stored in `podcast_publication_targets` with an `enabled` flag and a JSON `config`. Episodes finish asynchronously in `AudioGenerationService.doGenerateAudio`, which emits an `episode.generated` `PodcastEvent` on success. Podcast deletion is fully implemented (`PodcastService.delete` cascades sources, articles, episodes, audio files, and DB-level cascades for publication targets/publications), exposed at `DELETE /users/{userId}/podcasts/{podcastId}`.

The two gaps are: (1) nothing publishes automatically when an episode is generated, and (2) there is no UI to delete a podcast.

## Goals / Non-Goals

**Goals:**
- Let a user opt a podcast's FTP and/or SoundCloud target into automatic publishing, independently per target.
- Auto-publish each enabled target when an episode reaches `GENERATED`, reusing the existing manual publish path so behavior (same-day replacement, feed export, playlist rebuild) is identical.
- Provide a safe, hard-to-trigger-by-accident delete UI for podcasts.

**Non-Goals:**
- No change to the manual publish wizard or `PublishingService.publish` contract.
- No new backend delete logic (already exists).
- No retry/backoff for failed auto-publishes beyond the existing `FAILED` status and manual republish.

## Decisions

**1. Per-target `autoPublish` flag, separate from `enabled`.**
`enabled` already gates manual publishing ("this target is configured for this podcast"). Auto-publish is a distinct opt-in, so it gets its own boolean column `auto_publish`. A target must be both `enabled` and `autoPublish` to fire automatically. Alternative considered: a single podcast-level flag publishing to all enabled targets — rejected because users want to auto-FTP while keeping SoundCloud manual (quota limits).

**2. Trigger via an `@EventListener` on `episode.generated`, not inline in `AudioGenerationService`.**
Keeps publishing concerns inside the `publishing` package and avoids a new dependency edge from the audio/TTS layer into publishing. The listener filters `PodcastEvent` by `event == "episode.generated"`, loads the podcast (for `userId`) and episode, and publishes each `enabled && autoPublish` target. Alternative: call `publishingService.publish` directly from `doGenerateAudio` — rejected for coupling.

**3. Run publishing off the event thread on `Dispatchers.IO`.**
`publish()` performs network I/O (FTP upload, SoundCloud API). The event is published from a coroutine in `AudioGenerationService`; the listener launches its own `CoroutineScope(Dispatchers.IO + SupervisorJob())` job per the project concurrency rule (no `ExecutorService`). Each target is published in its own try/catch so one target's failure does not block the others; `publish()` already records `FAILED` and emits a failure event, so the listener only needs to log.

**4. Delete UI on the podcast detail page with typed-name confirmation.**
Reuse the existing shadcn `AlertDialog` pattern. The confirm button stays disabled until the typed text exactly equals the podcast name. On success, `DELETE` then redirect to `/podcasts`.

## Risks / Trade-offs

- [Auto-publish fires for an episode the user did not want published] → It only fires when the user has explicitly turned on both `enabled` and `autoPublish`; review-required podcasts still pass through human approval before reaching `GENERATED`.
- [Duplicate/racing publishes if `episode.generated` is emitted more than once] → `PublishingService.publish` is idempotent for an already-`PUBLISHED` episode+target (it routes to `updateExisting`) and replaces same-day publications, so a repeat event does not create duplicates.
- [A target fails silently] → Failure is recorded as `PublicationStatus.FAILED` and surfaced via the existing `episode.publish.failed` event and Publications tab; the listener logs the error.

## Migration Plan

- Flyway `V62` adds `auto_publish INTEGER NOT NULL DEFAULT 0`; existing rows default to off, so behavior is unchanged until a user opts in. Rollback is a column drop (or simply leaving the unused column, since older code ignores it).

## Open Questions

None.
