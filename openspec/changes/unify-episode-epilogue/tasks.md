## 1. Migration

- [x] 1.1 Add `V81__add_episode_score_script_hash.sql` adding a nullable `script_hash TEXT` column to `episode_scores`, and verify `mvn test` runs Flyway migration validation successfully

## 2. Epilogue

- [x] 2.1 Add the epilogue method to `EpisodeService` (judge trigger + existing recap/show-notes/sources sequence), moving `judgeInBackground` from `PodcastService` into it, and verify with a MockK-based unit test asserting the epilogue calls `EpisodeScoringService.scoreEpisode` in the background and still generates recap/show-notes/sources
- [x] 2.2 Verify the epilogue makes no judge call when `JudgeMode.OFF` is configured, with a unit test

## 3. Wire existing routes onto the epilogue (behavior-preserving)

- [x] 3.1 Route `EpisodeService.finalizeEpisode` through the epilogue instead of its inline recap/show-notes/sources calls, and verify with a MockK unit test that scheduled generation (`runGenerationPipeline`), focus generation, and retry all still produce recap/show-notes/sources
- [x] 3.2 Route `EpisodeService.createEpisodeFromPipelineResult` through the epilogue instead of its inline recap/show-notes/sources calls, and verify with a MockK unit test that regeneration still produces recap/show-notes/sources
- [x] 3.3 Remove `judgeInBackground` and its call site from `PodcastService.runGenerationPipeline` now that the epilogue (via `finalizeEpisode`) owns judging, and verify `mvn test` passes with no duplicate judge calls (assert `scoreEpisode` is invoked exactly once per generation in the relevant test)

## 4. Extend judging to routes that lacked it

- [x] 4.1 Verify (via the routing done in 3.1) that `doRetry` (`PodcastService.kt:93-178`) now triggers the epilogue's judge step, with a MockK unit test asserting `EpisodeScoringService.scoreEpisode` is called after a retry recomposes a script
- [x] 4.2 Switch `runFeedbackRecompose` (`PodcastService.kt:404-431`) from calling `episodeService.regenerateRecap` to calling the epilogue, and verify with a MockK unit test that a feedback recompose now triggers `EpisodeScoringService.scoreEpisode` in addition to recap regeneration

## 5. Score invalidation on rewrite

- [x] 5.1 Add a `scriptHash` field to `EpisodeScore` and populate it in `EpisodeScoringService.judgeAndStore` (hash of the script that was judged), and verify with a unit test that a freshly stored score carries a hash matching the judged script
- [x] 5.2 Update `scoreIfNeeded` to compare the existing score's `scriptHash` against the episode's current script hash: treat a `NULL` stored hash as matching (skip, preserving current behavior for the archive), delete-and-rejudge when they differ, and verify with unit tests covering: matching hash (skip), differing hash (delete + rejudge), null hash (skip)
- [x] 5.3 Handle a concurrent insert hitting the `(episode_id, scorer_version)` unique index in `judgeAndStore` gracefully (catch the constraint violation, re-read and return the winning row, do not fail the episode), and verify with a unit test. Confirmed via a read-only query that no `(episode_id, scorer_version)` duplicates exist in production, so no new migration is needed; the unique index already exists (`V68`).
- [x] 5.4 Remove the `EpisodeService` → `EpisodeScoringService` bean cycle: `EpisodeScoringService.scorePodcast` reads episodes via `EpisodeRepository` directly (mirroring `EpisodeService.findByPodcastIdPaged`) instead of depending on `EpisodeService`, and `EpisodeService` injects `EpisodeScoringService` as a plain constructor dependency instead of `ObjectProvider<EpisodeScoringService>`. Verify with `mvn test` (Spring context loads with no cycle) and update all test constructors accordingly.

## 6. Auto-publish exclusion for evaluation runs

- [x] 6.1 Inject `EvaluationRunRecorder` into `AutoPublishListener` and skip publishing entirely (no target attempted) when `runsForEpisode(episodeId)` is non-empty, and verify with a MockK unit test that an episode with a recorded `EvaluationRun` is not published to any target even when auto-publish targets are enabled
- [x] 6.2 Verify with a MockK unit test that an ordinary (non-eval) regenerated episode still auto-publishes exactly as before

## 7. Full verification

- [x] 7.1 Run `mvn test` and confirm all tests pass
- [x] 7.2 Restart the app (`./stop.sh && ./start.sh`) and confirm it starts cleanly (check `app.log` for startup errors)
- [x] 7.3 Retry judging is verified by `EpisodeEpilogueTest` (finalizeEpisode, which every retry path calls, judges exactly once); no live retry is run, because retrying a production episode regenerates it
- [x] 7.4 The evaluation-run exclusion is verified by `AutoPublishListenerTest`; no live eval regeneration is run, because it would create a production episode

## 8. Backfill

- [x] 8.1 After deploy, call `POST /users/743a9f46-0e1f-4898-a83e-94ae227a3cea/podcasts/85b9d107-f608-45be-a8f6-3ed1f731967a/episodes/226/scores` and confirm a score is returned/stored
- [x] 8.2 Call `POST /users/743a9f46-0e1f-4898-a83e-94ae227a3cea/podcasts/85b9d107-f608-45be-a8f6-3ed1f731967a/episodes/228/scores` and confirm a score is returned/stored
