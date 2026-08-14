## MODIFIED Requirements

### Requirement: SSE progress events
The preview SSE stream SHALL emit structured progress events at each pipeline stage transition, covering aggregation, scoring, deduplication and composition.

#### Scenario: Aggregation stage
- **WHEN** the pipeline begins aggregating unlinked posts
- **THEN** a `progress` event is emitted with `{stage: "aggregating"}`

#### Scenario: Scoring stage
- **WHEN** the pipeline begins scoring unscored articles
- **THEN** a `progress` event is emitted with `{stage: "scoring", articleCount: N}` where N is the number of articles to score

#### Scenario: Scoring progress
- **WHEN** articles are scored
- **THEN** further `progress` events are emitted with `{stage: "scoring", articleCount: N, scoredCount: M}` as M advances toward N

#### Scenario: Deduplication stage
- **WHEN** the pipeline begins the topic dedup filter
- **THEN** a `progress` event is emitted with `{stage: "deduplicating", articleCount: N}` where N is the number of eligible articles entering the filter

#### Scenario: Composing stage
- **WHEN** the pipeline begins composing the script
- **THEN** a `progress` event is emitted with `{stage: "composing", articleCount: N}` where N is the number of relevant articles being composed

#### Scenario: No unscored articles
- **WHEN** all articles are already scored (no scoring needed)
- **THEN** the scoring progress event is skipped and the pipeline proceeds directly to deduplication
