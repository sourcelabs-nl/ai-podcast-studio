## ADDED Requirements

### Requirement: Migration adds episode_history_fts virtual table

A new Flyway migration (next available `V` number, e.g. `V53__add_episode_history_fts.sql`) SHALL create:

- A SQLite FTS5 virtual table `episode_history_fts(episode_id UNINDEXED, podcast_id UNINDEXED, generated_at UNINDEXED, topics, recap, script_text, tokenize='porter unicode61')`.
- Triggers on `episodes` (AFTER INSERT and AFTER UPDATE OF `status`, `recap`, `script_text`) that insert or update the FTS row when status is `GENERATED`.
- Triggers on `episode_articles` (AFTER INSERT, AFTER UPDATE OF `topic`, AFTER DELETE) that recompute the joined `topics` column for the affected episode.
- A backfill statement populating the table from existing `GENERATED` episodes.

#### Scenario: Migration is idempotent under Flyway

- **WHEN** the application starts after the migration has already been applied
- **THEN** Flyway does not re-run it and the virtual table remains intact

#### Scenario: Backfill matches existing generated episode count

- **WHEN** the migration runs against a database with N `GENERATED` episodes
- **THEN** `SELECT count(*) FROM episode_history_fts` equals N immediately after migration
