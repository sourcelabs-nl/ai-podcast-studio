## ADDED Requirements

### Requirement: Migration adds deep-dive and research tracking

A new Flyway migration (next available `V` number, e.g. `V53__add_deep_dive_and_research_tracking.sql`) SHALL:

- Add column `deep_dive_enabled INTEGER NOT NULL DEFAULT 0` to `podcasts`.
- Add columns `research_calls INTEGER NOT NULL DEFAULT 0` and `research_cost_cents INTEGER` (nullable) to `episodes`.
- Create a `research_cache` table with at minimum `(query_hash TEXT PRIMARY KEY, query TEXT NOT NULL, max_results INTEGER NOT NULL, response_json TEXT NOT NULL, cached_at TIMESTAMP NOT NULL)`.

All changes MUST be additive and idempotent under Flyway.

#### Scenario: Existing podcasts default to disabled

- **WHEN** the migration runs against a database with existing rows in `podcasts`
- **THEN** every existing row has `deep_dive_enabled = 0`

#### Scenario: Existing episodes get default counts

- **WHEN** the migration runs against a database with existing rows in `episodes`
- **THEN** every existing row has `research_calls = 0` and `research_cost_cents IS NULL`

#### Scenario: Migration is idempotent under Flyway

- **WHEN** the application starts after the migration has already been applied
- **THEN** Flyway does not re-run it
