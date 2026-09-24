---
name: database-design
description: Database design rules for schema conventions, ID patterns, migrations, and query optimization. Applied to .kt and .sql files during code review. Includes SQLite-specific rules.
user_invocable: false
---

# Database Design Rules

5 rules covering schema design, data integrity, and query optimization. Applied to all `.kt` and `.sql` files.

---

## Rule DB1: Schema Design

Database schemas must follow relational best practices to ensure data integrity and query performance.

**Violations to flag:**
- Tables without a primary key
- Missing indexes on columns frequently used in WHERE clauses, JOIN conditions, or ORDER BY on tables expected to grow large
- Foreign key columns used in `@Query` WHERE clauses that have no corresponding `CREATE INDEX` in any Flyway migration
- Missing foreign key constraints between related tables
- Nullable columns that should have NOT NULL constraints based on business logic
- Missing cascading deletes where the application currently handles cascading manually in code
- Entity field names that don't correspond to their database column names (Kotlin camelCase should map to SQL snake_case via Spring Data JDBC's default naming strategy)
- New entities without a corresponding Flyway migration

**If the project uses SQLite:**
- Use `TEXT`, `INTEGER`, `REAL`, `BLOB` as column types
- Foreign key enforcement requires `PRAGMA foreign_keys = ON` (configured at connection level)
- SQLite only supports `ADD COLUMN` and `RENAME COLUMN` in ALTER TABLE

---

## Rule DB2: ID Patterns

Tables use `INTEGER PRIMARY KEY AUTOINCREMENT` for auto-increment IDs. UUID-based IDs are stored as `TEXT` and generated in application code.

**Violations to flag:**
- Using non-standard ID types without justification
- Hardcoded ID values in migration seed data when a subselect on a natural key would work

**Correct patterns:**
```sql
-- Auto-increment ID
CREATE TABLE store (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL
);

-- Seed data: use subselects on natural keys when referencing other tables
INSERT INTO orders (store_id, title)
VALUES ((SELECT id FROM store WHERE name = 'My Store'), 'Order 1');
```

---

## Rule DB3: Push Work to the Database

Sorting, ordering, filtering, aggregation, and deduplication should be done in database queries, not in application code. The database is optimized for these operations and can leverage indexes.

**Violations to flag:**
- `sortedBy`, `sortedByDescending`, `sortedWith` on collections fetched from the database
- `filter` or `partition` on full result sets when a WHERE clause could narrow the query
- `distinct`, `groupBy`, `count`, `sum`, `max`, `min` on collections when the database can do it
- Fetching all rows and then taking a subset (e.g., `.take(10)`) instead of using LIMIT in the query

**Correct pattern:**
```kotlin
// Bad: sort in application
fun getRecentOrders(): List<Order> {
    return repository.findAll().sortedByDescending { it.createdAt }
}

// Good: sort in database
fun getRecentOrders(): List<Order> {
    return repository.findAllByOrderByCreatedAtDesc()
}
```

---

## Rule DB4: No Defensive Null Checks on Persisted IDs

Entity IDs loaded from the database are always non-null. Filtering or null-checking `id` on collections fetched from the database is defensive noise that obscures intent and suggests the code does not trust the persistence layer.

**Violations to flag:**
- `.filter { it.id != null }` on collections loaded from a repository
- `.filterNotNull()` or `.mapNotNull { it.id }` when the collection comes from a database query
- `it.id!!` preceded by a `.filter { it.id != null }` guard (the filter is unnecessary)

**Not a violation:**
- Null checks on IDs of newly created entities that have not been persisted yet (ID is genuinely nullable before save)
- Null checks in mapper methods that handle both persisted and unpersisted entities

**Correct pattern:**
```kotlin
// Bad: defensive filter before associateBy
repository.findAll().filter { it.id != null }.associateBy { it.id!! }

// Good: trust the persistence layer
repository.findAll().associateBy { it.id!! }
```

---

## Rule DB5: Migration Conventions

Flyway migrations must follow strict naming and content rules. See also the `flyway-migration` skill for detailed guidance.

**Violations to flag:**
- Any change (even a seed value, comment, or whitespace) to a migration that already exists on `main` — migrations are append-only once committed; new behavior goes in a new forward migration. See the `flyway-migration` skill.
- Migration files not matching `V{number}__{description}.sql` naming convention
- Non-sequential version numbers (gaps in the sequence)
- Missing `ON DELETE CASCADE` on foreign keys for child entities managed via `@MappedCollection`

**If the project uses SQLite:**
- Using ALTER TABLE operations not supported by SQLite (only ADD COLUMN and RENAME COLUMN are supported)