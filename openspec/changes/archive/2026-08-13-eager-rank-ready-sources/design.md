## Context

Posts are polled into the `posts` table every ~60s by `SourcePollingScheduler` → `SourcePoller.poll()`. Aggregation (posts → articles via `SourceAggregator`) and LLM relevance scoring (`ArticleScoreSummarizer`) currently only happen inside `LlmPipeline.aggregateScoreAndFilter()` / `preview()`, triggered by episode generation or preview. As a result, non-aggregate sources show as unscored unlinked posts on the upcoming page until generation runs.

For non-aggregate sources (`SourceAggregator.shouldAggregate(source) == false`), each post maps 1:1 to an article (`mapIndividualPosts`), so there is nothing to wait for. Aggregate sources (Twitter/nitter) merge posts into threads that keep growing, so their scoring must stay deferred.

## Goals / Non-Goals

**Goals:**
- Aggregate + relevance-score non-aggregate sources eagerly after each poll round so they appear ranked on the upcoming page without generation/preview.
- Reuse existing `SourceAggregator` and `ArticleScoreSummarizer` so persisted scores are identical to generation-time scoring (no rework, net-zero total cost).
- Respect the per-podcast `maxLlmCostCents` cost gate for eager scoring.

**Non-Goals:**
- No change to aggregate-source (Twitter/nitter) behavior — they stay deferred.
- No frontend changes; the upcoming page already renders scored articles.
- No re-scoring of already-scored articles, and no change to dedup/compose stages.

## Decisions

**1. Place the eager path in `LlmPipeline.scoreReadySources(podcast)`.**
`LlmPipeline` already injects every collaborator needed (`sourceRepository`, `postRepository`, `articleRepository`, `sourceAggregator`, `articleScoreSummarizer`, `modelResolver`, `appProperties`) and already contains the aggregate→score sequence in `aggregateScoreAndFilter`. Adding a focused method here maximizes reuse and avoids a new component. Alternative considered: a dedicated `EagerScoringService` — rejected as unnecessary indirection given the existing collaborators.

**2. Eligibility = `!sourceAggregator.shouldAggregate(source)`.**
Reuses the single source of truth for the aggregate/non-aggregate distinction (handles the explicit `aggregate` flag and the Twitter/nitter defaults) rather than re-deriving it.

**3. Cost gate uses a scoring-only estimate.**
Eager scoring does not compose, so the existing full-pipeline estimate (`estimatePipelineCostCents`, which adds compose cost) would overstate. Extract the scoring portion into `CostEstimator.estimateScoringCostCents(articles, filterModel)` and have `estimatePipelineCostCents` call it, keeping both gates consistent. Compare against `podcast.maxLlmCostCents ?: appProperties.llm.maxCostCents`; skip the podcast's eager scoring if exceeded.

**4. Trigger inline at the end of `pollSources()`, scoped to due podcasts.**
After the poll `supervisorScope` completes, iterate `dueSources.map { it.podcastId }.distinct()`, resolve each podcast via `podcastService`, and call the eager path. Run **sequentially and inline** (not a separate `launch`) so two poll cycles can't concurrently pick the same unscored article from `findUnscoredBySourceIds` and double-score it. Per-cycle batches are small (only posts new since last poll), so the loop is not meaningfully delayed. Scoping to due podcasts avoids empty queries for every podcast every minute.

**5. Scheduler calls the service layer, not the pipeline directly.**
Per `.claude/rules/schedulers.md`, add a thin `PodcastService.scoreReadySources(podcast)` delegating to `LlmPipeline`, and have the scheduler call that. `PodcastService` already depends on `LlmPipeline`.

## Risks / Trade-offs

- [Eager scoring inline could slow the 60s poll loop if a podcast has many new posts] → Batches are bounded to posts new since the last poll; scoring is concurrent inside `ArticleScoreSummarizer`. If this ever becomes a problem, the trigger can move to a guarded background job later.
- [Topic/subtopic config changes after eager scoring leave stale scores] → Same risk already exists between polls today; acceptable. Scores are only advisory until generation's dedup/compose.
- [Cost gate semantics differ slightly from generation (scoring-only vs scoring+compose)] → Intentional and documented; eager scoring genuinely incurs only scoring cost.
- [`scoreSummarize` is blocking (`runBlocking` internally)] → Same pattern as existing `catchUpPoll`; runs on the IO scope, acceptable.

## Migration Plan

Pure additive behavior change; no schema or API change. Deploy by restart. Rollback = revert the scheduler trigger (the new methods become dead code with no effect). Already-eager-scored articles are simply skipped by generation's `findUnscoredBySourceIds`, so partial rollout is safe.

## Open Questions

None.
