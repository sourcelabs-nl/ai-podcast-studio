## Why

Every route that produces or rewrites an episode script repeats its own version of "what happens after the script is final": some call the judge in the background (`PodcastService.runGenerationPipeline`, `PodcastService.kt:521` calls `judgeInBackground`), some regenerate the recap, some do neither. The result is inconsistent coverage that nobody decided on purpose: `runFeedbackRecompose` (`PodcastService.kt:404-431`) never judges a rewritten focus script, `doRetry` (`PodcastService.kt:93-178`) never judges a retried episode, and `regenerateEpisodeAsync`/`runRegeneration` (`PodcastService.kt:596-659`) produces a real `GENERATED` episode through `EpisodeService.createEpisodeFromPipelineResult` without any epilogue step deciding whether it should auto-publish. Two further problems compound this: the judge's own skip check (`EpisodeScoringService.scoreIfNeeded`, `EpisodeScoringService.kt:83-99`) treats "already scored at the current version" as done forever, so a script that gets rewritten under the same episode id keeps its stale score instead of a fresh one; and `AutoPublishListener` (`AutoPublishListener.kt:40-42`) reacts to the generic `episode.generated` event with no awareness of whether the episode behind it was an ordinary generation or a `bypassLlmCache=true` evaluation run, so a regenerated eval sample can auto-publish exactly as a real episode would.

## What Changes

- Introduce one shared epilogue step that every script-producing/rewriting route calls once its script is final: it triggers the judge in the background (respecting `JudgeMode.OFF`) and performs the recap/show-notes/sources generation the route already needs. Routes that already do this today keep the same observable behavior; routes that skip a step today (judge on retry, judge on feedback recompose) gain it.
- Fix the judge's skip logic so a rewritten script (feedback recompose, regeneration, retry-from-compose) gets scored again instead of keeping its stale `SCORER_VERSION` row: the existing score for that episode is replaced rather than left in place when the script it was judging is no longer the current one.
- Ensure a regenerated evaluation run (`bypassLlmCache=true`) can never auto-publish: `AutoPublishListener` currently has no signal to distinguish an eval regeneration from an ordinary regeneration once both reach `GENERATED` and emit `episode.generated`.
- Backfill scores for episodes 226 and 228 via the existing scoring endpoint once the fix is deployed.

## Capabilities

### New Capabilities
- `episode-epilogue`: the shared post-composition step (judge + recap/show-notes/sources) that every episode-producing route calls, replacing each route's own ad hoc sequencing of those steps.

### Modified Capabilities
- `script-attention-scoring`: the "already-scored episodes are skipped" requirement changes from an unconditional per-episode-id skip to a skip that only holds while the score still describes the episode's current script; a rewrite invalidates and replaces the existing score.
- `episode-auto-publishing`: adds a requirement that an episode produced by an evaluation run (`bypassLlmCache=true`) is excluded from auto-publish regardless of the status it reaches.

## Impact

- `PodcastService.kt`: `runGenerationPipeline`, `doRetry`, `runFeedbackRecompose`, `runRegeneration` route their post-script steps through the new epilogue instead of their own inline sequencing.
- `EpisodeService.kt`: `createEpisodeFromPipelineResult` and `finalizeEpisode` delegate their recap/show-notes/sources sequencing to the epilogue rather than duplicating it.
- `EpisodeScoringService.kt`: `scoreIfNeeded` gains a check for whether the existing score still matches the episode's current script, and deletes/replaces it when not.
- `AutoPublishListener.kt` and/or the event it reacts to: gains the information needed to recognize and skip an eval regeneration.
- `EvaluationRunProvenance` / `EvaluationRun` (already recorded per `EvaluationRunRecorder.kt`): read from, not changed in shape, to make the auto-publish decision.
- Database: migration `V81` adds a nullable `script_hash` column to `episode_scores`; a stale score is replaced by delete-then-insert, keeping at most one row per `(episodeId, SCORER_VERSION)`.
- Operational: a one-time backfill call to `POST /users/{userId}/podcasts/{podcastId}/episodes/{episodeId}/scores` for episodes 226 and 228 for user `743a9f46-0e1f-4898-a83e-94ae227a3cea`, podcast `85b9d107-f608-45be-a8f6-3ed1f731967a`.
