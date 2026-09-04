## ADDED Requirements

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
