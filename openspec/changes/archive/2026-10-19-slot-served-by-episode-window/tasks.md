# Tasks

## 1. Data

- [x] 1.1 Migration `V87__add_episode_failed_at.sql` and `Episode.failedAt`; set it everywhere an episode is marked FAILED. Verify with an `EpisodeService` test that a failed run records `failedAt`, and `mvn test`.
- [x] 1.2 `EpisodeRepository.findFailedByPodcastIdAndWindowEnd` (focus and experiment excluded). Verify in `EpisodePurposeFilteringTest`.

## 2. Scheduler

- [x] 2.1 Compute the next slot from `findLatestCoveredWindowEnd(podcastId, now)`, falling back to `lastGeneratedAt`, then start of day; keep the earlier-day skip. Replace the interim `findEpisodeCoveringSlot`/`markSlotCovered`. Verify with `BriefingGenerationSchedulerTest`: re-run after discard serves the slot, discard reopens today's slot, GENERATING serves, missed days generate only today's slot.
- [x] 2.2 Retry policy: 0 failed → generate, 1 failed → generate after `failedAt + 5 min`, 2+ → skip. Verify with scheduler tests for 3 min, 6 min, two failures, and a manual retry in progress.

## 3. Discard and upcoming

- [x] 3.1 Remove the `last_generated_at` rollback from discard; `getUpcomingContent` starts at the latest served `window_end` with the same fallbacks. Update the affected `EpisodeServiceTest`/`PodcastServiceTest` cases; `mvn test` passes.

## 4. Live

- [x] 4.1 Restart; confirm in `app.log` that the scheduler reports no due slot for today (served by 297) and that the upcoming page starts at 2026-10-19T14:00Z.
