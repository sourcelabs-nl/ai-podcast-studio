## ADDED Requirements

### Requirement: Migration adds podcast compose_settings column

A new Flyway migration `V53__add_podcast_compose_settings.sql` SHALL add column `compose_settings TEXT` (nullable, no default) to the `podcasts` table. The column stores a JSON-encoded `Map<String, String>` and is (de)serialised via the existing `Map<String, String> ↔ JSON` converter registered in `SqliteDialectConfig`.

#### Scenario: Existing podcasts get null values

- **WHEN** the migration runs against a database with existing rows in `podcasts`
- **THEN** every existing row has `compose_settings IS NULL`

#### Scenario: Migration is idempotent under Flyway

- **WHEN** the application starts after the migration has already been applied
- **THEN** Flyway does not re-run it
