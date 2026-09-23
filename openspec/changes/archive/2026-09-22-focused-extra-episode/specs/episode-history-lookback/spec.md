## MODIFIED Requirements

### Requirement: searchPastEpisodes tool

The compose stage SHALL always register a `searchPastEpisodes` tool with the LLM. The tool SHALL take a string parameter `query` and SHALL return up to 5 ranked matches scoped to the current podcast. Each match SHALL include `episodeId`, `generatedAt` (ISO-8601 date), `topics` (joined list), `recapSnippet` (capped at ~280 characters), and `isFocusEpisode` (true when the matched episode has a non-null `focus`). Ranking SHALL use FTS5 `bm25`.

Full `script_text` MUST NOT be returned via the tool to bound token usage.

#### Scenario: Tool returns matches scoped to podcast
- **WHEN** the LLM calls `searchPastEpisodes("speckit")` and a prior episode of the current podcast mentioned speckit
- **THEN** the tool returns at least one match with the prior episode's id and recap snippet

#### Scenario: Tool ignores other podcasts
- **WHEN** the LLM calls `searchPastEpisodes("speckit")` for podcast A and only podcast B has prior speckit coverage
- **THEN** the tool returns an empty match list

#### Scenario: Tool returns recap snippet only
- **WHEN** any tool result is returned
- **THEN** the result object does not contain a full script text field

#### Scenario: Match identifies a focus episode
- **WHEN** the LLM calls `searchPastEpisodes` and one of the matches is a focus episode
- **THEN** that match's `isFocusEpisode` field is `true`

### Requirement: Compose prompt instructs the model to check history

The compose-stage prompts (monologue, dialogue, interview) SHALL include instructions directing the LLM to call `searchPastEpisodes` with relevant keywords before treating any topic as new, and to either skip the topic, treat it as a follow-up, or angle the segment as an update when the tool returns prior coverage. When a match's `isFocusEpisode` is `true`, the prompt SHALL instruct the LLM not to skip the topic outright: it SHALL treat it as a continuation, lead with new developments since that focus episode, and MAY reuse a limited number of the same articles rather than avoiding them entirely.

#### Scenario: Prompt mentions the tool
- **WHEN** the compose prompt is built for any style
- **THEN** the prompt text references `searchPastEpisodes` and instructs how to react to prior coverage

#### Scenario: Prior focus-episode coverage is continued, not skipped
- **WHEN** `searchPastEpisodes` returns a match with `isFocusEpisode = true` for the topic currently being composed
- **THEN** the composed script treats the topic as an ongoing continuation with new developments, rather than omitting it entirely
