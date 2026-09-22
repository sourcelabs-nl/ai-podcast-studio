## MODIFIED Requirements

### Requirement: Compose prompt instructs the model to check history

The compose-stage prompts (monologue, dialogue, interview) SHALL present the past-episode matches found before compose as a "Previously covered" block, each with its date, topics, recap snippet and whether it was a focus episode, and SHALL instruct the model to use the matches for framing and wording only: an article group with a `[FOLLOW-UP: ...]` header continues a story, one without is new, and a match alone is never grounds to skip or demote a story. A focus-episode match SHALL be treated as a continuation. When the podcast aired focus episodes since the previous regular episode, the prompt SHALL name them and tell the model to treat an overlapping subject as a follow-up. The prompts MUST NOT reference a `searchPastEpisodes` tool.

#### Scenario: Prompt mentions the tool
- **WHEN** the compose prompt is built for any style and a past episode matched a research subject
- **THEN** the prompt contains the previously covered block with that episode and does not reference a `searchPastEpisodes` tool

#### Scenario: No matches
- **WHEN** no past episode matched
- **THEN** the prompt contains no previously covered block

## REMOVED Requirements

### Requirement: searchPastEpisodes tool

**Reason**: A tool call forced a second compose round; the lookups are predictable from the topic clusters.
**Migration**: The pre-compose research stage runs the same podcast-scoped full-text search for each research subject (`precompose-research`), returning the same fields and never the full script text.

### Requirement: Per-episode history-lookup budget

**Reason**: There is no tool to budget.
**Migration**: The up-front lookup is capped at 5 subjects with 5 matches each (`precompose-research`).
