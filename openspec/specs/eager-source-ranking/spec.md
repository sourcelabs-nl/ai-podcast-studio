# eager-source-ranking Specification

## Purpose
Scoring and ranking articles for non-aggregate sources as soon as polling finishes, ahead of the scheduled run, within the per-podcast LLM cost gate.
## Requirements
### Requirement: Eager ranking of non-aggregate sources after polling
After each poll round completes, the system SHALL eagerly aggregate and relevance-score the non-aggregate sources of every podcast that had at least one source due in that round, so their articles are ranked before any episode generation or preview. A source is eligible for eager ranking only when `SourceAggregator.shouldAggregate(source)` returns `false` (RSS, website, YouTube, or any source explicitly flagged non-aggregate). Aggregate sources (Twitter/nitter) SHALL NOT be eagerly ranked and remain deferred to script generation/preview.

Eager ranking SHALL be implemented as `LlmPipeline.scoreReadySources(podcast)` and triggered through a `PodcastService` delegate so the scheduler calls the service layer. For each eligible podcast it SHALL: (1) aggregate the unlinked posts of non-aggregate sources into articles via `SourceAggregator.aggregateAndPersist` (1:1 mapping), (2) find the unscored articles of those sources, and (3) score them via `ArticleScoreSummarizer.scoreSummarize`, persisting `relevanceScore`, `summary`, `subtopic`, and token usage exactly as generation does. Articles scored eagerly SHALL NOT be re-scored at generation time.

#### Scenario: Non-aggregate source ranked after polling
- **WHEN** a poll round saves new posts for an enabled RSS source whose podcast was due
- **THEN** after the round those posts are aggregated 1:1 into articles and scored, so the upcoming page shows them with relevance scores without any episode generation or preview

#### Scenario: Aggregate source not eagerly ranked
- **WHEN** a poll round saves new posts for a Twitter/nitter source (`shouldAggregate == true`)
- **THEN** those posts are left as unlinked, unscored posts and are aggregated and scored only at generation/preview time

#### Scenario: Eagerly-scored articles not re-scored at generation
- **WHEN** an episode is generated for a podcast whose non-aggregate sources were already eagerly scored
- **THEN** those articles are skipped by the scoring stage (they are no longer unscored) and only newly-arrived or aggregate-source articles are scored

#### Scenario: No unscored ready articles is a no-op
- **WHEN** the eager-ranking step runs for a podcast with no unscored non-aggregate-source articles
- **THEN** no LLM scoring calls are made

### Requirement: Eager ranking respects the per-podcast LLM cost gate
Eager ranking SHALL respect the per-podcast cost limit `maxLlmCostCents` (falling back to `app.llm.max-cost-cents`). Before scoring, the system SHALL estimate the scoring-only cost of the podcast's unscored ready-source articles via `CostEstimator.estimateScoringCostCents`. If the estimate exceeds the threshold, eager scoring SHALL be skipped for that podcast and the articles SHALL be left for scoring at generation time. When pricing is not configured for the model (estimate is null), eager scoring SHALL proceed.

#### Scenario: Cost gate skips eager scoring
- **WHEN** the estimated scoring cost of a podcast's unscored ready-source articles exceeds its `maxLlmCostCents` threshold
- **THEN** eager scoring is skipped for that podcast and a warning is logged, leaving the articles unscored until generation

#### Scenario: Cost gate passes
- **WHEN** the estimated scoring cost is within the threshold
- **THEN** the ready-source articles are scored eagerly

#### Scenario: Scoring-only estimate excludes composition cost
- **WHEN** the eager cost gate estimates cost for a set of unscored articles
- **THEN** the estimate covers only LLM relevance scoring (not script composition), so it matches the actual cost eager ranking incurs

