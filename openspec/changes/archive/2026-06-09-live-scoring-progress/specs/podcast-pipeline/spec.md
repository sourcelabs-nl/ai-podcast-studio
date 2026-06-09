## ADDED Requirements

### Requirement: Incremental scoring progress events
During the `scoring` stage, the pipeline SHALL emit progress as articles complete, not only a one-time total. `ArticleScoreSummarizer` SHALL accept a progress callback invoked as each article finishes scoring (whether it succeeded or exhausted its retries), reporting the running completed count and the total. To avoid flooding the event bus on large batches, callbacks SHALL be throttled to at most roughly 50 per run, and the final completion SHALL always be reported. The pipeline SHALL forward these into `scoring` stage events that carry both `articleCount` (total) and `scoredCount` (completed so far). This applies to both the generation path and the preview path.

#### Scenario: Progress reported as articles complete
- **WHEN** 318 unscored articles are being scored
- **THEN** the pipeline emits multiple `scoring` stage events whose `scoredCount` increases toward `articleCount`, and the final event reports `scoredCount` equal to `articleCount`

#### Scenario: Throttled on large batches
- **WHEN** a large batch of articles is scored
- **THEN** the number of progress callbacks is bounded (roughly 50 per run) rather than one per article

#### Scenario: Failed articles still advance progress
- **WHEN** an article exhausts its scoring retries and is skipped
- **THEN** it still increments the completed count so progress can reach the total

#### Scenario: Progress is transient
- **WHEN** scoring progress is emitted
- **THEN** progress is delivered via SSE only and is not persisted to the database per article
