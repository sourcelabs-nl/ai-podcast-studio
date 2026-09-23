## Context

The routes that end with a finished episode script are, today:

| Route | Entry point | Judge today? | Recap/show-notes/sources today? |
|---|---|---|---|
| Scheduled/manual generation | `PodcastService.runGenerationPipeline`, `PodcastService.kt:451-528`, `finalizeEpisode` at `:520` | Yes, `judgeInBackground(episode)` at `:521` | Yes, via `EpisodeService.finalizeEpisode` |
| Focus generation | `PodcastService.runFocusGenerationPipeline` / `composeAndFinalizeFocusEpisode`, `:333-370`, `finalizeEpisode` at `:369` | No | Yes, via `finalizeEpisode` |
| Feedback recompose (focus) | `PodcastService.runFeedbackRecompose`, `:404-431` | No | Yes, explicit `episodeService.regenerateRecap(updated, podcast)` at `:425` |
| Retry | `PodcastService.doRetry`, `:93-178`; `finalizeEpisode` at `:150`, `:171`, `:175` | No | Yes, via `finalizeEpisode` |
| Regeneration | `PodcastService.regenerateEpisodeAsync` / `runRegeneration`, `:596-659`; `EpisodeService.createEpisodeFromPipelineResult` | No | Yes, inline in `createEpisodeFromPipelineResult` (`:207-211`) |
| Preview | `PodcastService.previewBriefing` → `LlmPipeline.preview` | No | No (persists nothing) |

Both places that do recap/show-notes/sources (`EpisodeService.finalizeEpisode`, `:489-549`, and `EpisodeService.createEpisodeFromPipelineResult`, `:145-224`) run the same three steps in the same order: `generateAndStoreRecap` → `generateAndStoreShowNotes` → `generateSourcesFile`. That sequence already exists as a de facto shared shape; it is simply duplicated rather than named.

`EpisodeScoringService.scoreIfNeeded` (`:83-99`) treats "a score row exists at `SCORER_VERSION`" as terminal. It has no concept of the script having changed since that row was written, because until this change no route that rewrites a script also asks to be judged again.

`AutoPublishListener.onEpisodeGenerated` (`AutoPublishListener.kt:40-82`) reacts to the podcast-wide `episode.generated` event, which both `EpisodeService.finalizeEpisode` (`:542-546`) and `EpisodeService.createEpisodeFromPipelineResult` (`:217-221`) publish whenever `requireReview` is false. `runRegeneration` (`PodcastService.kt:628-659`) calls `createEpisodeFromPipelineResult` with no argument that would suppress this event or mark the resulting episode as an eval sample; only the presence of an `EvaluationRun` row (written by `EvaluationRunRecorder.record`, gated on `EvaluationRunProvenance` being non-null, i.e. `bypassLlmCache = true`) distinguishes an eval regeneration from a real one after the fact.

## Goals / Non-Goals

**Goals:**
- One function/component ("the epilogue") that every route in the table above calls after it has a final script, replacing each route's private sequencing of judge + recap + show notes + sources.
- Make the judge re-run when a script changes under an episode id that was already scored, instead of silently keeping a stale score.
- Make it impossible for an evaluation regeneration to auto-publish, using the retained `EvaluationRun` row rather than adding new episode-level state, since a bug here should not require a schema change to fix quickly.
- Preserve every existing scenario's outward behavior except the two the proposal calls out as bugs (missing judge calls; missing eval-run exclusion).

**Non-Goals:**
- Not building `PipelineRunner`/`RunSpec` or any of the run-identity work from `pipeline-runner` (that change depends on this one, not the reverse).
- Not changing the judge's prompt, scoring math, or `SCORER_VERSION` semantics beyond "a score that no longer matches the current script is not final".
- Not changing preview: it persists nothing today and stays out of the epilogue's scope (there is nothing to judge or recap durably).

## Decisions

**1. The epilogue is a single service method taking the episode, podcast, and the topic labels the recap needs, called at the exact point in each route where the previous code called `finalizeEpisode`/`createEpisodeFromPipelineResult`/`regenerateRecap` inline.** It lives alongside `EpisodeService` (it already owns `generateAndStoreRecap`, `generateAndStoreShowNotes`, `generateSourcesFile`, and would otherwise need to expose all three publicly to a caller in `PodcastService`). `judgeInBackground` moves from `PodcastService` into this same method so the "judge after the script is final" decision has one owner instead of living partly in `PodcastService` and partly wherever recap is triggered.

  `EpisodeScoringService`'s only reason to depend on `EpisodeService` was the paged read `scorePodcast` uses (`findByPodcastIdPaged`); it now reads directly from `EpisodeRepository` (mirroring `findByPodcastIdPaged`'s own `findByPodcastId`/`findByPodcastIdAndStatusIn` split), which removes the cycle entirely and lets `EpisodeService` inject `EpisodeScoringService` as a plain constructor dependency instead of through an `ObjectProvider`. The judge runs on a `SupervisorJob` scope owned by `EpisodeService` and is not launched at all under `JudgeMode.OFF`. A route that rewrites a script in place (feedback recompose) calls `runEpilogueForRewrite`, which derives the recap's topic labels from the episode's article links; `finalizeEpisode` keeps an existing recap (a retry resuming at TTS) but still judges.

  *Alternative considered*: a separate `EpisodeEpilogueService`. Rejected for this change's scope — it would mean threading a new bean through both `PodcastService` and `EpisodeService` for logic that already lives entirely inside `EpisodeService`'s existing private methods. `pipeline-runner` is expected to promote this into its own component once the runner exists to own it; doing that split now is premature.

**2. `finalizeEpisode` and `createEpisodeFromPipelineResult` call the epilogue instead of inlining recap/show-notes/sources, and both start calling the judge, which they do not today.** This is the mechanism by which retry (`finalizeEpisode`) and regeneration (`createEpisodeFromPipelineResult`) gain judging without `PodcastService` needing to change per-route.

**3. `runFeedbackRecompose` switches from calling `episodeService.regenerateRecap(...)` directly to calling the epilogue**, so a feedback-recomposed focus episode is judged for the first time.

**4. The score-invalidation check is added inside `EpisodeScoringService.scoreIfNeeded`, keyed on the episode's `scriptText`.** A hash of the script that was judged is stored on the score row (new column) rather than trusting `updatedAt`/wall-clock comparisons, because the epilogue's judge call is asynchronous and can race the row that trigged it; comparing content is unambiguous regardless of ordering. When the existing score's script hash does not match the episode's current script, `scoreIfNeeded` deletes the stale row (there is exactly one row per `(episodeId, SCORER_VERSION)` per `EpisodeScoreRepository.findByEpisodeIdAndScorerVersion`) and proceeds to judge and insert a fresh one.

  *Alternative considered*: keep the stale row and insert a second one, distinguishing by `scoredAt`. Rejected: `findByEpisodeIdAndScorerVersion` returning a single row is relied on elsewhere (`existingScores` orders by version, not recency within a version), and two rows for the same `(episodeId, SCORER_VERSION)` pair would silently become ambiguous to every reader that assumes at most one.

  This requires a new migration (`V81`) adding a `script_hash` column to `episode_scores`; a `NULL` value for a pre-existing row means "unknown", and `scoreIfNeeded` treats `NULL` as matching (skip) so archived episodes are not all re-judged the moment this ships.

  Two concurrent judging runs for the same episode (the epilogue's judge call racing a manual `scorePodcast`/backfill call, for instance) can both pass the "no existing row" check before either inserts. `episode_scores` already carries a `UNIQUE INDEX idx_episode_scores_episode_version` on `(episode_id, scorer_version)` (from `V68`), so the loser's insert fails on that constraint rather than producing a duplicate row; `judgeAndStore` catches that specific constraint violation (`isConstraintViolation`), re-reads the winning row, and returns it instead of failing the episode. No new migration is needed for this: the unique index already exists and a direct read-only check of the production database found no duplicate `(episode_id, scorer_version)` rows.

**5. Auto-publish exclusion is implemented by having `AutoPublishListener` ask `EvaluationRunRecorder.runsForEpisode(episodeId)` (already exists, `EvaluationRunRecorder.kt:64-65`) and skip entirely (no target attempted, nothing logged as failed) when it returns a non-empty list.** No new column or event field is needed: the existing `evaluation_runs` table already records exactly the condition ("this episode's script came from a cache-bypassed compose") that must gate auto-publish.

  *Alternative considered*: add a `purpose`/`isEvaluationRun` flag to `Episode` itself and check that instead. Rejected for this change: it duplicates state that `EvaluationRun` already holds, and `pipeline-runner` is explicitly bringing an `episodes.purpose` column (Phase B) that will supersede this check; adding a parallel boolean now would mean removing it again in the very next change.

## Risks / Trade-offs

- [Judging a rewritten script means the judge model call happens more often than before (retry and feedback recompose now cost a judge call they never made).] → Bounded and desired: these are exactly the routes the proposal says should be judged; cost is one model call per rewrite, same as one generation.
- [Adding `script_hash` to `episode_scores` and backfilling nothing for existing rows means an old score's staleness can never be detected retroactively.] → Acceptable: `NULL` treated as "not stale" preserves current behavior for the archive; only scripts rewritten after this ships get the fresh-score guarantee, which matches what shipped code can affect.
- [`AutoPublishListener` doing a repository lookup on every `episode.generated` event adds a query to a listener that was previously log-only on the fast path.] → Negligible: `runsForEpisode` is a single indexed lookup, and the listener already does two `findById` calls (`podcastService.findById`, `episodeService.findById`) before this would run.

## Migration Plan

1. Add migration `V81__add_episode_score_script_hash.sql` (nullable `script_hash TEXT` on `episode_scores`).
2. Implement the epilogue in `EpisodeService`, wire `finalizeEpisode`, `createEpisodeFromPipelineResult`, and `runFeedbackRecompose` (via `PodcastService`) onto it, one route at a time, with a test and a restart+live-check after each.
3. Implement the score-invalidation check in `scoreIfNeeded`.
4. Implement the `EvaluationRunRecorder` check in `AutoPublishListener`.
5. Deploy. Backfill scores for episodes 226 and 228 via `POST /users/{userId}/podcasts/{podcastId}/episodes/{episodeId}/scores`.
6. Rollback: the migration only adds a nullable column, so reverting the code is safe without a down-migration; a future `V82` may drop the column if this is ever reverted for good.
