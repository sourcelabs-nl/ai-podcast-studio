# Proposal

## Why

Whether a slot still needs an episode is decided from `podcast.last_generated_at`, while which articles belong to it is decided from the episode's own window. The two drift apart: a discard rolls `last_generated_at` back, a re-run leaves it alone, and on 19 October that combination made the scheduler generate the 16:00 slot twice (episodes 297 and 298). The episodes already record exactly which slot they serve in `window_end`, so they can be the only source of truth.

## What Changes

- A slot is served when an episode of the podcast has `window_end` equal to the slot and a status of GENERATING, GENERATING_AUDIO, GENERATED, PENDING_REVIEW or APPROVED. The scheduler computes the next slot from the latest served `window_end`, not from `last_generated_at`.
- A failed slot is retried automatically once, as a new episode, at least 5 minutes after the failure. After a second failure the slot stays open for a manual retry or re-run.
- Discard no longer rolls `last_generated_at` back: the discarded episode simply stops serving its slot, so the slot is open again (and the scheduler fills it if it is today's slot).
- The upcoming page lists articles published after the latest served `window_end` instead of after `last_generated_at`.
- `last_generated_at` stays as a display value ("last generated") and is still written when an episode is created, but nothing decides on it any more. It remains the fallback for a podcast whose episodes predate the window columns.
- **BREAKING (behaviour)**: discarding today's episode makes the scheduler generate a replacement, as it effectively did before through the rollback; discarding an episode of an earlier day does not, because earlier-day slots are skipped.

## Capabilities

### New Capabilities

### Modified Capabilities
- `episode-article-window`: adds "A slot is served by an episode whose window ends at it" and "A failed slot is retried once".

## Impact

- `BriefingGenerationScheduler` (slot computation, retry policy), `EpisodeRepository` (served and failed lookups), `EpisodeService` (discard without rollback, `failed_at`), `PodcastService.getUpcomingContent`.
- New migration: `episodes.failed_at`.
- Replaces the interim check `findEpisodeCoveringSlot` / `markSlotCovered` added earlier on 19 October.
