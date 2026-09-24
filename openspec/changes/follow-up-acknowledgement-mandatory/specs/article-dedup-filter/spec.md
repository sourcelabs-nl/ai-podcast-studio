## MODIFIED Requirements

### Requirement: The dedup annotations outrank the history-lookup tool
The compose prompt SHALL treat the `[FOLLOW-UP: ...]` headers as authoritative for what is new and what is a continuation. The `searchPastEpisodes` tool SHALL remain available for referencing prior coverage accurately and for avoiding repeated phrasing, but a keyword match alone SHALL NOT be grounds to skip a story or demote it out of the lead, and the prompt SHALL state that an article carrying no `[FOLLOW-UP: ...]` header is to be treated as new. For an article group carrying a `[FOLLOW-UP: ...]` header, the prompt SHALL require the script to tell the listener, briefly and when introducing the group, that the show covered the story before, basing that acknowledgement only on the header and never stating when it aired or what was said beyond it.

Dedup compares candidate titles and summaries against the actual set of historical episode articles. The tool matches keywords against past scripts, so a product name recurring in a different story reads as a hit. Instructed to treat any hit as prior coverage, the composer claimed the GPT-6 Astra launch had been "covered yesterday" on three keyword matches, when the previous episode never mentioned it and the older match was a pre-release benchmark story, and it demoted the day's lead story accordingly. Permitted rather than required to acknowledge an annotated continuation, the composer narrated most of them as fresh news.

#### Scenario: A keyword hit does not demote an unannotated story
- **WHEN** `searchPastEpisodes` returns matches for a story that carries no `[FOLLOW-UP: ...]` header
- **THEN** the prompt requires the story still be treated as new and to remain eligible for the lead

#### Scenario: Prior coverage may be asserted from an annotation
- **WHEN** an article group carries a `[FOLLOW-UP: ...]` header
- **THEN** the prompt requires the script to acknowledge that prior coverage in one short line when introducing the group, based only on the header

#### Scenario: The rule reaches every composer
- **WHEN** a briefing, dialogue or interview prompt is built
- **THEN** it contains the annotation-primacy rule
