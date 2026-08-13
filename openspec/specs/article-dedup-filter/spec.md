# Capability: Article Dedup Filter

## Purpose

Topic-based deduplication filter that clusters candidate articles by topic, compares them against historical episode articles, and returns a filtered, annotated set for composition.
## Requirements
### Requirement: Topic dedup filter clusters and deduplicates articles before composition
The system SHALL provide a `TopicDedupFilter` component that clusters candidate articles by topic, compares them against historical episode articles, and returns a filtered, annotated set for composition. The filter SHALL use the filter model (cheap LLM) via a single call.

The filter SHALL receive:
- Candidate articles: list of articles with `id`, `title`, and `summary`
- Historical articles: list of articles from recent GENERATED episodes, with `episodeId`, `articleId`, `title`, and `summary`

The filter SHALL output a structured result with a `clusters` array. Each cluster SHALL contain:
- `topic`: short label describing the topic
- `status`: `NEW` (not covered in any recent episode) or `CONTINUATION` (covered before, with or without new developments)
- `previousContext`: (CONTINUATION only) one sentence describing what was covered before
- `candidateArticleIds`: all candidate article IDs in this cluster
- `selectedArticleIds`: article IDs to keep for composition (empty means skip the topic entirely)

#### Scenario: New topic with single article passes through
- **WHEN** a candidate article about "NVIDIA Blackwell GPU pricing" has no matching topic in historical articles
- **THEN** the filter creates a NEW cluster with that article in `selectedArticleIds`

#### Scenario: New topic with multiple articles selects top 3
- **WHEN** 5 candidate articles all cover "GPT-5 benchmark leaks" and no historical article covers this topic
- **THEN** the filter creates a NEW cluster with the 3 most comprehensive/complementary articles in `selectedArticleIds`

#### Scenario: New topic with 3 or fewer articles keeps all
- **WHEN** 2 candidate articles cover "Rust async runtime changes" with no historical match
- **THEN** the filter creates a NEW cluster with both articles in `selectedArticleIds`

#### Scenario: Continuation topic with new development keeps articles
- **WHEN** candidate articles cover "Gemini 2.5 pricing" and historical articles from a recent episode covered "Gemini 2.5 release and benchmarks"
- **THEN** the filter creates a CONTINUATION cluster with `previousContext` describing the prior coverage, and `selectedArticleIds` containing up to 3 articles with the new pricing information

#### Scenario: Continuation topic with no new development skips all
- **WHEN** candidate articles cover "Claude Code 1M context" and historical articles already covered the same announcement with the same facts
- **THEN** the filter creates a CONTINUATION cluster with `selectedArticleIds` empty

#### Scenario: Cross-source duplicates within today's batch merged into one cluster
- **WHEN** TechCrunch and The Verge both publish articles about the same Google announcement today
- **THEN** both articles appear in the same cluster, and the filter selects up to 3 with complementary coverage

#### Scenario: High-scoring single-source article not over-clustered
- **WHEN** a unique research paper about "formal verification of LLM agents" has no close match in other candidates or historical articles
- **THEN** the filter creates a single-article NEW cluster rather than merging it with loosely related topics

### Requirement: Dedup filter transforms output into annotated article blocks for composer
The system SHALL transform the dedup filter's cluster output into the composer's article input format. For CONTINUATION clusters with selected articles, the system SHALL prepend a `[FOLLOW-UP: ...]` header above the article group in the composer prompt, containing the `previousContext` from the cluster. For NEW clusters, articles SHALL appear without any header annotation.

#### Scenario: Continuation articles annotated with follow-up header
- **WHEN** a CONTINUATION cluster has `previousContext` = "Gemini 2.5 was released with benchmarks in a recent episode" and selected articles about pricing
- **THEN** the composer prompt includes `[FOLLOW-UP: Gemini 2.5 was released with benchmarks in a recent episode -- today's articles cover new pricing details]` above those articles

#### Scenario: New topic articles have no annotation
- **WHEN** a NEW cluster has 2 selected articles about a brand-new topic
- **THEN** the articles appear in the composer prompt without any `[FOLLOW-UP: ...]` header

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

