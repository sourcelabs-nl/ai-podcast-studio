---
paths:
  - "src/main/kotlin/com/aisummarypodcast/store/**/*.kt"
---

# Entity authoring rules (`@Table` classes)

Persisted classes in this package are annotated with `@Table("snake_case")`. (Note: this codebase does not use the `*Entity` suffix the `spring-data-jdbc` skill describes; entity and domain are combined here. Match the existing pattern, do not introduce the suffix in a one-off.)

**Do**
- IDs: `@Id val id: Long? = null` (auto-increment) or `@Id val id: String? = null` (app-generated UUID). Null id signals "insert" to Spring Data.
- Child collections within the same aggregate: `@MappedCollection(idColumn = "...")` typed as `Set<T>`, not `List<T>`.
- References to other aggregates: plain `Long`/`String` id fields, never `@MappedCollection`.

**Singleton config tables** (one seeded row, e.g. `BackupSettings`): a non-null `@Id val id: Long = SINGLETON_ID` is the accepted exception, because Flyway seeds the row, so every `save()` is an update. This is intentional, not a violation.

**Don't**
- Use `Int`/`@Id val id: Int = 0` for IDs.
- Add a new entity without a matching Flyway migration.

Full rules: `spring-data-jdbc` skill (Rules 1-5) and `database-design` skill (DB2).
