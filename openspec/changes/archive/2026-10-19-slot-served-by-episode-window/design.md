# Design

## Context

`BriefingGenerationScheduler.checkAndGenerate` computes `cron.next(lastGeneratedAt ?: startOfDay)`, skips earlier-day slots, and generates when the result is in the past. `last_generated_at` is written on episode creation (except re-run, regeneration, focus), rolled back on discard to the latest covering episode, and read by `PodcastService.getUpcomingContent`. `EpisodeWindowResolver` already starts a window at the latest covered `window_end` when that is earlier than the previous slot (via `findLatestCoveredWindowEnd`). An interim check (`findEpisodeCoveringSlot`) skips a slot that an episode already ends at.

## Goals / Non-Goals

**Goals:** one source of truth for "served"; bounded automatic retries.

**Non-Goals:** removing the `last_generated_at` column or its display; changing how windows are computed; changing retry/re-run endpoints.

## Decisions

**Served = episode with `window_end` = slot and a non-failed, non-discarded status.** This is the filter `findLatestCoveredWindowEnd` already uses, so window resolution and scheduling agree by construction. The scheduler starts from `findLatestCoveredWindowEnd(podcastId, now)`. Alternative, keep writing `last_generated_at` correctly in every path: rejected, that is the drift that caused episode 298.

**Earlier-day slots stay skipped.** Starting from the last served `window_end` after a long gap would otherwise yield an old slot; the existing skip loop moves to today's slot and the window resolver extends the window back to the last served end, so the gap's articles land in one episode.

**Retry as a new episode, counted from FAILED episodes for the slot.** `count(FAILED with window_end = slot)`: 0 → generate; 1 → generate once `failed_at + 5 min <= now`; 2+ → skip, logged at DEBUG so a stopped slot does not log a line per minute. A new episode keeps counting simple; resuming the failed episode would reset its status and lose the count. The LLM cache makes the repeated stages cheap.

**`failed_at` column.** No episode timestamp records the failure; `generated_at` is the creation time and a run can fail 20 minutes later. Written wherever an episode is marked FAILED. Rows failed before the migration have none and count as failed long enough ago.

**Discard stops rolling back.** With "served" derived from episodes, the rollback has nothing left to fix and only re-creates the drift.

**Upcoming starts at the latest served `window_end`.** That is the exact start of the next window; `last_generated_at` approximated it.

## Risks / Trade-offs

- [A podcast whose last served episode has no window (pre-migration) cannot be placed] → fall back to `last_generated_at`, then start of day, as today.
- [Discarding today's episode now visibly triggers a replacement within a minute] → same outcome as the rollback produced, and the spec names it; a re-run beforehand serves the slot instead.
- [Two schedulers ticking concurrently] → not possible: one loop per process.
