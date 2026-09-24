---
name: flyway-migration
description: Use when creating, renaming, or modifying Flyway database migration files (db/migration/V*__*.sql). Covers versioning rules, naming conventions, and common pitfalls.
user-invocable: false
---

# Flyway Database Migrations

## Overview

This project uses Flyway for database schema versioning with Spring Boot auto-configuration. Migration files live in `src/main/resources/db/migration/` and follow Flyway's versioned migration naming convention.

## When This Applies

- Creating a new database migration file
- Renaming or renumbering a migration
- Troubleshooting Flyway startup errors
- Adding columns, tables, or indexes to the schema

## Naming Convention

```
V<version>__<description>.sql
```

- **Version**: Integer, monotonically increasing (e.g., `V34`)
- **Separator**: Exactly two underscores (`__`)
- **Description**: snake_case summary of the change (e.g., `add_discount_to_stores`)

Examples:
```
V34__add_discount_to_stores.sql
V35__add_user_preferences_table.sql
```

## Critical Rule: Unique Version Numbers

Each migration **must** have a unique version number. Flyway will refuse to start if two files share the same version:

```
FlywayException: Found more than one migration with version 10
Offenders:
-> V10__add_cost_tracking.sql
-> V10__add_discount_to_stores.sql
```

### Before creating a new migration

1. List existing migrations to find the highest version number:
   ```
   ls src/main/resources/db/migration/ | sort -V | tail -1
   ```
2. Use the **next** integer after the highest existing version.

### If you encounter a duplicate version error

Rename the newer migration file to the next available version number. Never rename a migration that has already been applied to a database (its checksum is recorded in `flyway_schema_history`).

## Migration Content Guidelines

### If the project uses SQLite

Keep these SQLite-specific rules in mind:

- `ALTER TABLE` only supports `ADD COLUMN` and `RENAME COLUMN` (no `DROP COLUMN` before SQLite 3.35.0)
- No `ALTER TABLE ... ALTER COLUMN` — to change a column type, create a new table, copy data, drop the old one, and rename
- Use `TEXT` for strings, `INTEGER` for booleans and ints, `REAL` for floats
- Foreign keys require `PRAGMA foreign_keys = ON` (handled by Spring Boot config)

### Keep migrations small and focused

- One logical change per migration (e.g., one new column or one new table)
- Name should clearly describe the change

### Never modify an applied migration

A migration file is immutable once it has been committed and pushed: never edit, rename, or delete an existing `V<n>__*.sql`. This includes changes that look harmless, such as a seed value, a comment, or whitespace: Flyway's checksum covers the entire file, so any byte change counts. The only exception is a migration that has never been merged to `main` and was never applied anywhere shared; that one can still be edited freely.

When Flyway applies a migration it records the file's checksum in `flyway_schema_history`. On every later startup it re-validates each applied migration against the current file. If the file changed, the checksums differ and Flyway fails to start:

```
Migration checksum mismatch for migration version <n>
Validate failed: Migrations have failed validation
```

This failure hits every database that already applied the old version: local, staging, and production alike. If you need to fix a mistake in an applied migration, create a **new** migration with the corrective DDL, never edit the old one.

**Recovery, if an applied migration was edited by mistake:**

1. Restore the file to its committed content so the checksum matches what the databases already applied (`git show <good-sha>:<path>`).
2. If the change was actually wanted, re-express it as a new forward migration.
3. Any database that already booted with the bad file and recorded the new checksum needs a one-time realignment: run `./mvnw flyway:repair`, or locally where data is disposable, null the row (`UPDATE flyway_schema_history SET checksum = NULL WHERE version = '<n>';`) and let the next startup recompute it, or recreate the DB.

## Stale `target/` Copies

When running from a packaged JAR (`./start.sh`), Flyway reads migrations from the JAR's `BOOT-INF/classes/`. If `target/classes/db/migration/` has stale copies from a previous build, a rebuild (`./mvnw package`) is needed to pick up renamed or new migrations.

## Quick Decision Guide

| Situation | Action |
|---|---|
| Adding a new column | Create `V<next>__add_<column>_to_<table>.sql` with `ALTER TABLE ... ADD COLUMN` |
| Adding a new table | Create `V<next>__add_<table>_table.sql` with `CREATE TABLE` |
| Adding an index | Create `V<next>__add_<name>_index.sql` with `CREATE INDEX` |
| Fixing a bad migration | Create a new corrective migration, never edit the old one |
| Duplicate version error | Rename the newer file to the next available version |
| Checksum mismatch | If safe, run `./mvnw flyway:repair` to update the stored checksum |
