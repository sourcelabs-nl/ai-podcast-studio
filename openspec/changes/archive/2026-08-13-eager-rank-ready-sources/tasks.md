## 1. Cost estimation

- [x] 1.1 Add `CostEstimator.estimateScoringCostCents(articles, filterModel): Int?` extracting the scoring portion of `estimatePipelineCostCents`
- [x] 1.2 Refactor `estimatePipelineCostCents` to call the new helper for its scoring component

## 2. Eager ranking pipeline path

- [x] 2.1 Add `LlmPipeline.scoreReadySources(podcast)`: filter sources to `!sourceAggregator.shouldAggregate(it)`, aggregate their unlinked posts via `aggregateAndPersist`, find unscored articles for those source ids
- [x] 2.2 Apply the cost gate using `estimateScoringCostCents` vs `podcast.maxLlmCostCents ?: appProperties.llm.maxCostCents`; skip + warn-log if exceeded
- [x] 2.3 Score via `articleScoreSummarizer.scoreSummarize` with source labels from `extractDomainAndPath`; no-op early when there are no unscored ready articles

## 3. Service delegate

- [x] 3.1 Add `PodcastService.scoreReadySources(podcast)` delegating to `LlmPipeline.scoreReadySources`

## 4. Scheduler trigger

- [x] 4.1 In `SourcePollingScheduler.pollSources()`, after the poll `supervisorScope` and before setting `lastPollRoundCompletedAt`, iterate distinct `dueSources` podcast ids, resolve each via `podcastService.findById`, and call `podcastService.scoreReadySources`, wrapping each in try/catch so one failure does not abort the others or the loop

## 5. Tests

- [x] 5.1 `LlmPipeline.scoreReadySources` tests (MockK): non-aggregate sources scored, aggregate sources skipped, cost-gate skip, empty no-op, eagerly-scored articles not re-scored
- [x] 5.2 `CostEstimator.estimateScoringCostCents` test and a check that `estimatePipelineCostCents` still sums scoring + composition
- [x] 5.3 `SourcePollingScheduler` test that the post-round step calls the service for each due podcast once and isolates failures

## 6. Verify

- [x] 6.1 `mvn test` green
- [x] 6.2 Restart app (`./stop.sh && ./start.sh`); confirm non-aggregate sources for podcast `85b9d107-...` show relevance scores on `/upcoming` after a poll round, Twitter/nitter remain unscored, and the cost-gate skip logs when `maxLlmCostCents` is tiny
