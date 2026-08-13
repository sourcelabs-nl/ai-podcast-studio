## Why

On the upcoming-episode page, non-aggregate sources (RSS, website, YouTube) show up as unscored, unlinked posts until an episode is generated or previewed, even though each of their posts maps 1:1 to an article and could be ranked immediately. Only aggregate sources (Twitter/nitter) genuinely need to defer scoring, because their posts merge into threads that keep growing. Ranking ready sources right after polling makes the upcoming page useful sooner and speeds up later generation, at no extra total LLM cost.

## What Changes

- After each poll round, the source-polling scheduler triggers eager aggregation + LLM relevance scoring for non-aggregate sources of the podcasts that were polled this round.
- A source is eligible for eager ranking only when `SourceAggregator.shouldAggregate(source)` is `false` (RSS, website, YouTube, or any source explicitly flagged non-aggregate). Aggregate sources (Twitter/nitter) remain deferred to generation/preview as today.
- Eager scoring respects the per-podcast `maxLlmCostCents` cost gate using a scoring-only cost estimate; if the estimated cost of a podcast's unscored ready-source articles exceeds the threshold, eager scoring is skipped for that podcast (those articles get scored later during generation).
- Scoring reuses the existing `ArticleScoreSummarizer` and `SourceAggregator`; scores/summaries/subtopics persist exactly as during generation, so the work is not repeated later (net-zero total LLM cost, faster generation).
- No frontend change: the upcoming page already renders scored articles with relevance badges, so eagerly-scored non-aggregate sources surface automatically.

## Capabilities

### New Capabilities
- `eager-source-ranking`: Eagerly aggregate and relevance-score non-aggregate sources after polling, gated by per-podcast LLM cost, so their articles are ranked before episode generation.

### Modified Capabilities
- `source-polling`: The poll round now triggers eager source ranking for polled podcasts as a post-round step.

## Impact

- `LlmPipeline` — new `scoreReadySources(podcast)` method.
- `PodcastService` — thin delegate entry point for the scheduler.
- `CostEstimator` — new scoring-only `estimateScoringCostCents`, reused by the existing pipeline estimate.
- `SourcePollingScheduler` — post-round trigger for due podcasts.
- No API or schema changes. No frontend changes. No new dependencies.
