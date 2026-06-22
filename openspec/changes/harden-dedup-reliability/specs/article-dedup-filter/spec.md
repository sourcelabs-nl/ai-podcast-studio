## MODIFIED Requirements

### Requirement: Dedup filter fetches historical articles from recent episodes
The system SHALL fetch historical articles by joining `episode_articles` with `articles` for the most recent N GENERATED episodes (where N is the podcast's `recapLookbackEpisodes` or the global default). Only episodes with status `GENERATED` SHALL be included. The deduplicated historical set SHALL then be capped to the most-recent `app.llm.dedup.max-historical-articles` articles (default 120) before being passed to the filter. The historical block embedded in the dedup prompt SHALL include article **titles only** (summaries SHALL NOT be embedded), since titles are sufficient for topic recall and full summaries needlessly bloat the prompt. Each historical title SHALL be truncated to a fixed maximum length so that an oversized source title (e.g. a Twitter/Nitter post embedded as a title) cannot dominate the prompt.

#### Scenario: Historical articles fetched from last 7 generated episodes
- **WHEN** the dedup filter runs for a podcast with default lookback (7) and there are 10 GENERATED episodes
- **THEN** historical articles are fetched from the 7 most recent GENERATED episodes only

#### Scenario: Discarded and pending episodes excluded from history
- **WHEN** the most recent 3 episodes are DISCARDED and the 4th is GENERATED
- **THEN** historical articles are only fetched from GENERATED episodes, skipping the discarded ones

#### Scenario: Historical articles capped to the configured maximum
- **WHEN** the lookback episodes contain 300 distinct historical articles and `max-historical-articles` is 120
- **THEN** only the 120 most-recent historical articles are passed to the filter

#### Scenario: Historical block contains titles without summaries
- **WHEN** a historical article has a title "GLM-5.2 launches" and a non-empty summary
- **THEN** the dedup prompt's historical block contains the title but NOT the summary text

#### Scenario: Oversized historical title is truncated
- **WHEN** a historical article's title is a 500-character Twitter post embedded as the title
- **THEN** the dedup prompt's historical block contains a truncated form of the title (with an ellipsis), not the full 500 characters

### Requirement: Dedup filter uses a non-reasoning model and fails the episode on error
The dedup stage SHALL use a non-reasoning model (default `deepseek/deepseek-v4-flash`). Reasoning models are unsuitable because their reasoning tokens are charged against the output-token cap and can return empty content, which previously caused dedup to be silently skipped.

`TopicDedupFilter` SHALL retry the dedup LLM call (at least once) on failure. If the call still fails, the error SHALL propagate: the system SHALL NOT silently fall back to composing un-deduped articles. During episode generation a propagated dedup failure SHALL fail the episode (status `FAILED`, retryable). During preview a propagated dedup failure SHALL be surfaced as an error to the caller.

#### Scenario: Dedup failure fails the episode
- **WHEN** the dedup LLM call fails on every retry during episode generation
- **THEN** the episode is marked FAILED and is NOT composed with un-deduped articles

#### Scenario: Dedup failure surfaces during preview
- **WHEN** the dedup LLM call fails on every retry during a preview
- **THEN** the preview reports an error rather than returning an un-deduped script

#### Scenario: Empty filtered result still skips the episode (not an error)
- **WHEN** dedup succeeds but every cluster selects no articles (all continuations with no new developments)
- **THEN** the pipeline skips briefing generation for that run without raising an error

## REMOVED Requirements

### Requirement: Dedup filter behavior remains unchanged
**Reason**: The dedup filter's input is changing — the historical block is now title-only and capped — so the prior "behavior remains unchanged" guarantee no longer holds.
**Migration**: None. The `searchPastEpisodes` compose-time tool referenced by the old requirement remains additive and is unaffected; only the dedup historical-context format and size changed.
