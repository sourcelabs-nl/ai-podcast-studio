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

### Requirement: Dedup filter salvages a truncated response, and otherwise fails the episode
The dedup stage SHALL state an explicit reasoning effort of `none` rather than omitting the reasoning block, and SHALL NOT rely on the model it runs on to be non-reasoning.

The dedup model reasons by default: OpenRouter reports `~deepseek/deepseek-v4-flash-latest` as `default_enabled: true` at `default_effort: "high"`, and an absent reasoning parameter is inferred from that default. Because reasoning tokens are charged against the same output-token cap as the JSON, omitting the block consumed the whole budget and returned empty content, failing episode 200 on five successive attempts. Measured on the live API for that model, an omitted block cost 47 reasoning tokens on a one-line task where an explicit `effort: "none"` cost none; a bounded `effort: "low"` cost 22 on the same task but still consumed a full 23,940-token dedup budget on a real prompt, so bounding the effort is not sufficient and only `none` is.

`TopicDedupFilter` SHALL parse the response strictly first. When the strict parse fails because the response is truncated, the filter SHALL recover the complete cluster objects from the incomplete `clusters` array instead of discarding the response. A truncated dedup response is safe to act on because an article that no surviving cluster mentions is simply not selected for composition, which is the conservative outcome.

A salvaged response SHALL be accepted only when it still selects at least `app.compose.max-articles` articles. At or above that count the truncated tail provably could not have changed what gets composed, because the compose cap would have discarded the surplus anyway. A salvage SHALL be logged at WARN with the recovered cluster count and the selected article count.

`TopicDedupFilter` SHALL retry the dedup LLM call (at least once) when neither a strict parse nor an acceptable salvage is available. If no attempt yields either, the error SHALL propagate: the system SHALL NOT silently fall back to composing un-deduped articles. During episode generation a propagated dedup failure SHALL fail the episode (status `FAILED`, retryable). During preview a propagated dedup failure SHALL be surfaced as an error to the caller.

#### Scenario: Dedup requests no reasoning
- **WHEN** the dedup request is built for an `openrouter` model
- **THEN** the extra body's `reasoning` object carries `effort` `none` and `exclude`, rather than the block being omitted

#### Scenario: Truncated response with enough clusters is salvaged
- **WHEN** the dedup response is cut off mid-array after 234 complete clusters that together select at least `app.compose.max-articles` articles
- **THEN** the 234 clusters are used, a WARN records the salvage, and the episode is composed from them

#### Scenario: Truncated response with too few clusters is retried
- **WHEN** the dedup response is cut off after 3 complete clusters selecting 5 articles and `app.compose.max-articles` is 40
- **THEN** the attempt fails and the retry applies rather than composing from 5 articles

#### Scenario: Articles beyond the truncation point are not composed
- **WHEN** a salvaged response's surviving clusters mention only some of the candidate articles
- **THEN** the unmentioned candidates are not selected for composition

#### Scenario: Dedup failure fails the episode
- **WHEN** the dedup LLM call yields neither a strict parse nor an acceptable salvage on every retry during episode generation
- **THEN** the episode is marked FAILED and is NOT composed with un-deduped articles

#### Scenario: Dedup failure surfaces during preview
- **WHEN** the dedup LLM call fails on every retry during a preview
- **THEN** the preview reports an error rather than returning an un-deduped script

#### Scenario: Empty filtered result still skips the episode (not an error)
- **WHEN** dedup succeeds but every cluster selects no articles (all continuations with no new developments)
- **THEN** the pipeline skips briefing generation for that run without raising an error

### Requirement: Dedup filter bounds its output with a candidate-scaled token budget
`TopicDedupFilter` SHALL cap the dedup call's output tokens with a budget derived from the number of candidate articles, rather than a fixed value. The budget SHALL be `90 * candidateCount`, clamped to a floor of 8000 and a ceiling of 32000 tokens.

The budget SHALL scale with the candidate count because the prompt requires every candidate article to appear in exactly one cluster, so a legitimate response's length is proportional to its input: observed runs returned 29 clusters for 37 candidates, 39 for 39, 50 for 56 and 44 for 68. At roughly 90 output tokens per cluster (topic label, status, a CONTINUATION's `previousContext` sentence and the selected ids), a 183-candidate day needs some 16,000 tokens. A fixed 8000-token cap truncated episode 191's response mid-array at cluster 234 and cost the episode.

The ceiling SHALL be retained so that a degenerating response (for example a repetition loop emitting near-duplicate clusters indefinitely) is still cut off in seconds rather than streaming for minutes. The ceiling SHALL stay well inside the dedup model's context window.

#### Scenario: Budget scales with a large candidate set
- **WHEN** dedup runs with 183 candidate articles
- **THEN** the request's output-token cap is 16470, not 8000

#### Scenario: Small candidate set gets the floor
- **WHEN** dedup runs with 12 candidate articles
- **THEN** the request's output-token cap is the 8000-token floor, not 1080

#### Scenario: Very large candidate set is bounded by the ceiling
- **WHEN** dedup runs with 900 candidate articles
- **THEN** the request's output-token cap is the 32000-token ceiling, not 81000

### Requirement: Follow-up annotations are persisted with the episode's article links
The follow-up context the dedup filter produces for a CONTINUATION cluster SHALL be persisted on the episode-article link, as `episode_articles.follow_up_context`, alongside the `topic` and `topic_order` already stored there. It SHALL be persisted on every path that writes a link, so that both a scheduled generation and a regeneration store it.

The column SHALL be nullable: a NEW cluster's articles carry no context, and links written before the column existed read back as no annotation.

#### Scenario: A continuation's context is persisted
- **WHEN** dedup returns a CONTINUATION cluster whose `previousContext` is "Gemini 2.5 was released with benchmarks in a recent episode"
- **THEN** each selected article's episode-article link stores that context

#### Scenario: A new topic stores no context
- **WHEN** dedup returns a NEW cluster
- **THEN** its articles' links store a null follow-up context

#### Scenario: A pre-existing link reads back as unannotated
- **WHEN** an episode's links were written before the column existed
- **THEN** they read back with no follow-up context rather than failing

### Requirement: The dedup annotations outrank the history-lookup tool
The compose prompt SHALL treat the `[FOLLOW-UP: ...]` headers as authoritative for what is new and what is a continuation. The `searchPastEpisodes` tool SHALL remain available for referencing prior coverage accurately and for avoiding repeated phrasing, but a keyword match alone SHALL NOT be grounds to skip a story or demote it out of the lead, and the prompt SHALL state that an article carrying no `[FOLLOW-UP: ...]` header is to be treated as new.

Dedup compares candidate titles and summaries against the actual set of historical episode articles. The tool matches keywords against past scripts, so a product name recurring in a different story reads as a hit. Instructed to treat any hit as prior coverage, the composer claimed the GPT-6 Astra launch had been "covered yesterday" on three keyword matches, when the previous episode never mentioned it and the older match was a pre-release benchmark story, and it demoted the day's lead story accordingly.

#### Scenario: A keyword hit does not demote an unannotated story
- **WHEN** `searchPastEpisodes` returns matches for a story that carries no `[FOLLOW-UP: ...]` header
- **THEN** the prompt requires the story still be treated as new and to remain eligible for the lead

#### Scenario: Prior coverage may be asserted from an annotation
- **WHEN** an article group carries a `[FOLLOW-UP: ...]` header
- **THEN** the script may reference that prior coverage

#### Scenario: The rule reaches every composer
- **WHEN** a briefing, dialogue or interview prompt is built
- **THEN** it contains the annotation-primacy rule

### Requirement: Dedup logs an abnormal finish reason
`TopicDedupFilter` SHALL log the dedup response's finish reason at WARN whenever it is anything other than a normal stop, together with the output-token budget the request carried. When the finish reason is a normal stop, or when the provider reports no finish reason at all, no such log SHALL be emitted.

Budget exhaustion and a transport fault were previously indistinguishable in the logs. A response the model cut off at the cap raises `OpenAIInvalidDataException("Error reading response")` from the client, the same message a read timeout produces, so episode 200's five failed attempts gave no indication that the budget was the cause. The finish reason is the field that separates them: the first run after this was added reported `LENGTH against an output budget of 23940 tokens`, which identified the cause immediately.

#### Scenario: A truncated response names the budget
- **WHEN** the dedup response's finish reason is `LENGTH` for a request whose output-token budget was 23940
- **THEN** a WARN records the finish reason and that budget

#### Scenario: A normal completion logs no warning
- **WHEN** the dedup response's finish reason is a normal stop
- **THEN** no finish-reason warning is logged

#### Scenario: A missing finish reason logs no warning
- **WHEN** the provider reports no finish reason
- **THEN** no finish-reason warning is logged, because a provider that reports nothing says nothing about the budget
