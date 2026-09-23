# episode-history-lookback Specification

## Purpose
TBD - created by archiving change add-episode-history-lookback. Update Purpose after archive.
## Requirements
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

### Requirement: Compose prompt instructs the model to check history

The compose-stage prompts (monologue, dialogue, interview) SHALL present the past-episode matches found before compose as a "Previously covered" block, each with its date, topics, recap snippet and whether it was a focus episode, and SHALL instruct the model to use the matches for framing and wording only: an article group with a `[FOLLOW-UP: ...]` header continues a story, one without is new, and a match alone is never grounds to skip or demote a story. A focus-episode match SHALL be treated as a continuation. When the podcast aired focus episodes since the previous regular episode, the prompt SHALL name them and tell the model to treat an overlapping subject as a follow-up. The prompts MUST NOT reference a `searchPastEpisodes` tool.

#### Scenario: Prompt mentions the tool

- **WHEN** the compose prompt is built for any style and a past episode matched a research subject
- **THEN** the prompt contains the previously covered block with that episode and does not reference a `searchPastEpisodes` tool

#### Scenario: No matches

- **WHEN** no past episode matched
- **THEN** the prompt contains no previously covered block
