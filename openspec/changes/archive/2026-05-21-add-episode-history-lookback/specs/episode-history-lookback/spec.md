## ADDED Requirements

### Requirement: FTS5 index over episode history

The system SHALL maintain a SQLite FTS5 virtual table `episode_history_fts` indexing every `GENERATED` episode's `recap`, `script_text`, and the comma-joined list of `episode_articles.topic` values. The index MUST be kept in sync via triggers that fire on insert/update of `episodes.status='GENERATED'`, on update of `episodes.recap` or `episodes.script_text`, and on insert/update/delete of `episode_articles.topic`.

The initial migration SHALL backfill the index from all existing `GENERATED` episodes.

#### Scenario: New generated episode is indexed

- **WHEN** an episode transitions to status `GENERATED`
- **THEN** a corresponding row exists in `episode_history_fts` whose `script_text`, `recap`, and `topics` columns match the episode

#### Scenario: Recap update propagates

- **WHEN** an episode's `recap` is regenerated
- **THEN** the matching `episode_history_fts` row reflects the new `recap`

#### Scenario: Episode article topic change propagates

- **WHEN** a row in `episode_articles` has its `topic` updated for a `GENERATED` episode
- **THEN** the `topics` column in the matching `episode_history_fts` row reflects the updated value

#### Scenario: Backfill covers existing data

- **WHEN** the FTS migration has run on a database with N pre-existing `GENERATED` episodes
- **THEN** `SELECT count(*) FROM episode_history_fts` equals N

### Requirement: searchPastEpisodes tool

The compose stage SHALL always register a `searchPastEpisodes` tool with the LLM. The tool SHALL take a string parameter `query` and SHALL return up to 5 ranked matches scoped to the current podcast. Each match SHALL include `episodeId`, `generatedAt` (ISO-8601 date), `topics` (joined list), and `recapSnippet` (capped at ~280 characters). Ranking SHALL use FTS5 `bm25`.

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

### Requirement: Per-episode history-lookup budget

The compose stage SHALL enforce a maximum of 5 `searchPastEpisodes` invocations per episode generation. Calls beyond the cap MUST return an empty result list with a `budgetExhausted=true` indicator and MUST NOT throw.

#### Scenario: Budget exhausts gracefully

- **WHEN** the LLM has already invoked `searchPastEpisodes` 5 times and attempts a 6th call
- **THEN** the tool returns an empty list with `budgetExhausted=true`

### Requirement: Compose prompt instructs the model to check history

The compose-stage prompts (monologue, dialogue, interview) SHALL include instructions directing the LLM to call `searchPastEpisodes` with relevant keywords before treating any topic as new, and to either skip the topic, treat it as a follow-up, or angle the segment as an update when the tool returns prior coverage.

#### Scenario: Prompt mentions the tool

- **WHEN** the compose prompt is built for any style
- **THEN** the prompt text references `searchPastEpisodes` and instructs how to react to prior coverage
