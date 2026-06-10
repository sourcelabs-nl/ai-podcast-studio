---
paths:
  - "src/main/resources/db/migration/V*__*.sql"
---

# Flyway migration authoring rules (SQLite)

**Before creating a file**: find the highest existing version (`ls src/main/resources/db/migration/ | sort -V | tail -1`) and use the next integer. Name it `V<n>__<snake_case_description>.sql` (exactly two underscores).

**Do**
- Keep version numbers sequential, no gaps, unique.
- Use SQLite column types: `TEXT`, `INTEGER`, `REAL`, `BLOB`.
- `INTEGER PRIMARY KEY AUTOINCREMENT` for auto-increment ids; `TEXT` for app-generated UUIDs.
- Add `ON DELETE CASCADE` on foreign keys for child entities managed via `@MappedCollection`.
- Add an index for any foreign-key column used in a `@Query` WHERE/JOIN/ORDER BY on a table expected to grow.
- Seed referencing rows with subselects on natural keys, not hardcoded ids.

**Don't**
- Never edit a migration already applied (its checksum is recorded in `flyway_schema_history`). Add a new migration instead.
- Use ALTER TABLE operations beyond `ADD COLUMN` / `RENAME COLUMN` (all SQLite supports).

Full rules: `flyway-migration` skill and `database-design` skill (DB1, DB2, DB5).
