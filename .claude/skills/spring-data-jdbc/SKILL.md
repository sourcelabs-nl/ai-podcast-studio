---
name: spring-data-jdbc
description: Spring Data JDBC patterns and rules for this project. Used by the code-reviewer agent to validate entity, repository, and migration changes. Adapted for SQLite.
user_invocable: false
---

# Spring Data JDBC Rules

These rules codify the Spring Data JDBC patterns established in this codebase. All entity, repository, and migration changes must conform to these rules. This project uses **SQLite** as the database.

---

## Rule 1: Aggregate Root Pattern

Only aggregate roots get repositories. Child entities are managed via `@MappedCollection` on the parent. Never create separate repositories for child entities.

**Violation:**
```kotlin
// Child entity with its own repository
@Table("episode_article")
data class EpisodeArticleEntity(@Id val id: Long? = null, val episodeId: Long)

interface EpisodeArticleRepository : CrudRepository<EpisodeArticleEntity, Long>
```

**Correct:**
```kotlin
// Child entity managed through aggregate root
@Table("episode")
data class EpisodeEntity(
    @Id val id: Long? = null,
    @MappedCollection(idColumn = "episode_id")
    val articles: Set<EpisodeArticleEntity> = emptySet()
)

interface EpisodeRepository : CrudRepository<EpisodeEntity, Long>
```

**Note:** This is an aspirational pattern. The current codebase may have standalone child repositories for historical reasons. Apply this rule to new code. When child entities are accessed independently (e.g., queried by their own columns), a separate repository is acceptable.

---

## Rule 2: Collection Type

Child collections use `Set<T>`, not `List<T>`.

**Violation:**
```kotlin
@MappedCollection(idColumn = "episode_id")
val articles: List<EpisodeArticleEntity> = emptyList()
```

**Correct:**
```kotlin
@MappedCollection(idColumn = "episode_id")
val articles: Set<EpisodeArticleEntity> = emptySet()
```

---

## Rule 3: Entity Naming

Database entities use the `*Entity` suffix and include a `@Table("snake_case")` annotation.

**Violation:**
```kotlin
data class Episode(@Id val id: Long? = null)
```

**Correct:**
```kotlin
@Table("episode")
data class EpisodeEntity(@Id val id: Long? = null)
```

---

## Rule 4: ID Patterns

Aggregate roots and entities use `@Id val id: Long? = null` (auto-increment) or `@Id val id: String? = null` (application-generated UUID strings).

**Violation:**
```kotlin
@Table("podcast")
data class PodcastEntity(@Id val id: Int = 0)
```

**Correct:**
```kotlin
// Auto-increment Long ID
@Table("podcast")
data class PodcastEntity(@Id val id: Long? = null)
```

**Singleton config-table exception (not a violation):** A settings/config table that holds exactly one row seeded by Flyway may use a non-null `@Id` with a fixed default, e.g. `@Id val id: Long = SINGLETON_ID`. Because Flyway seeds the row, Spring Data JDBC always treats `save()` as an update (the id is never null), which is the intended behavior. This is a deliberate deviation from `@Id val id: Long? = null` for the singleton pattern and should not be flagged, provided the migration seeds the row.

```kotlin
// Acceptable: singleton settings row, seeded by Flyway
@Table("backup_settings")
data class BackupSettings(@Id val id: Long = SINGLETON_ID, val enabled: Boolean) {
    companion object { const val SINGLETON_ID = 1L }
}
```

---

## Rule 5: Cross-Aggregate References

References to other aggregates use plain ID fields (Long or String), never `@MappedCollection`. Only parent-child relationships within the same aggregate use `@MappedCollection`.

**Violation:**
```kotlin
@Table("episode")
data class EpisodeEntity(
    @Id val id: Long? = null,
    @MappedCollection(idColumn = "podcast_id")
    val podcast: PodcastEntity  // cross-aggregate!
)
```

**Correct:**
```kotlin
@Table("episode")
data class EpisodeEntity(
    @Id val id: Long? = null,
    val podcastId: Long  // plain reference
)
```

---

## Rule 6: Repository Interfaces

Repositories extend `CrudRepository<T, ID>`. Add `PagingAndSortingRepository<T, ID>` when pagination is needed.

**Correct:**
```kotlin
interface PodcastRepository : CrudRepository<PodcastEntity, Long>

// When pagination is needed
interface EpisodeRepository : CrudRepository<EpisodeEntity, Long>, PagingAndSortingRepository<EpisodeEntity, Long>
```

---

## Rule 7: Custom Queries

Custom queries use `@Query` with named `@Param` parameters. SQLite does not support type casting (no `::uuid`, `::text`).

**Violation:**
```kotlin
@Query("SELECT * FROM episode WHERE podcast_id = ?1")
fun findByPodcastId(podcastId: Long): List<EpisodeEntity>
```

**Correct:**
```kotlin
@Query("SELECT * FROM episode WHERE podcast_id = :podcastId")
fun findByPodcastId(@Param("podcastId") podcastId: Long): List<EpisodeEntity>
```

Use `@Modifying` for write queries:
```kotlin
@Modifying
@Query("DELETE FROM episode WHERE podcast_id = :podcastId")
fun deleteByPodcastId(@Param("podcastId") podcastId: Long)
```

---

## Rule 8: No Custom Converters

Enums are stored as strings natively. Do not create `ReadingConverter` or `WritingConverter` classes.

**Violation:**
```kotlin
@ReadingConverter
class StatusConverter : Converter<String, EpisodeStatus> {
    override fun convert(source: String): EpisodeStatus = EpisodeStatus.valueOf(source)
}
```

**Correct:**
```kotlin
// Just use the enum directly, Spring Data JDBC handles it
enum class EpisodeStatus { GENERATED, APPROVED, PUBLISHED, DISCARDED }

@Table("episode")
data class EpisodeEntity(
    @Id val id: Long? = null,
    val status: EpisodeStatus
)
```

---

## Rule 9: Domain-Entity Separation

Separate domain classes from entity classes. Use mapper functions or extension functions to convert between them. Entities are never exposed to controllers.

**Violation:**
```kotlin
// Controller directly using entity
@GetMapping("/podcasts/{id}")
fun getPodcast(@PathVariable id: Long): PodcastEntity = repository.findById(id).get()
```

**Correct:**
```kotlin
// Extension function converts entity to domain
fun PodcastEntity.toDomain(): Podcast = Podcast(
    id = this.id!!,
    name = this.name,
    description = this.description
)

// Service returns domain objects
@Service
class PodcastService(private val repository: PodcastRepository) {
    fun getPodcast(id: Long): Podcast =
        repository.findById(id).map { it.toDomain() }.orElseThrow()
}
```

---

## Rule 10: Cascade Behavior

Child entities cascade through the aggregate root. Use `ON DELETE CASCADE` on foreign keys at the database level, and `@MappedCollection` at the application level.

**Violation:**
```sql
-- Missing cascade
CREATE TABLE episode_article (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    episode_id INTEGER NOT NULL REFERENCES episode(id)
);
```

**Correct:**
```sql
CREATE TABLE episode_article (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    episode_id INTEGER NOT NULL REFERENCES episode(id) ON DELETE CASCADE
);
```

---

## Rule 11: Prefer Derived Query Methods Over @Query

Do not use `@Query` when Spring Data can derive the query from the method name. This applies to simple filters, ordering, counting, date ranges, and boolean flags. `@Query` is only justified for JOINs, subqueries, aggregations, or complex filter combinations.

**Violation:**
```kotlin
@Query("SELECT * FROM episode WHERE podcast_id = :podcastId")
fun findByPodcastId(@Param("podcastId") podcastId: Long): List<EpisodeEntity>
```

**Correct:**
```kotlin
fun findByPodcastId(podcastId: Long): List<EpisodeEntity>
```

**Justified `@Query` usage** (cannot be derived):
```kotlin
// JOINs
@Query("""
    SELECT e.* FROM episode e
    JOIN episode_article ea ON e.id = ea.episode_id
    WHERE ea.article_id = :articleId
""")
fun findByArticleId(@Param("articleId") articleId: Long): List<EpisodeEntity>
```

---

## Rule 12: No Manual Pagination

Do not reimplement functionality already provided by `CrudRepository` or `PagingAndSortingRepository`. Never write manual `LIMIT`/`OFFSET` pagination or hand-rolled custom repository fragments for paging when a derived query method will do.

**The pattern for paginated list endpoints in this codebase:**

1. **Repository** extends both `CrudRepository<T, ID>` and `PagingAndSortingRepository<T, ID>`, with derived methods returning `Page<T>` and accepting `Pageable`. No `@Query`, no custom impl, no `LIMIT`/`OFFSET`.
2. **Service** takes a `Pageable`, returns `Page<T>`. Domain stays in the Spring Data type.
3. **Controller** parses `?page=N&pageSize=M` (validate `page >= 0`, `1 <= pageSize <= 200`) and any filter params manually, builds a `PageRequest.of(page, pageSize, Sort.by(...))`, calls the service, then maps the resulting `Page<T>` to the project's `PagedResponse<T>` envelope DTO via `Page.toResponse { it.toResponse() }`. The framework `Page<T>` type MUST NOT be returned from the controller — the API schema is owned by `PagedResponse<T>`, not by Spring's serializer.

**Violation (manual pagination):**
```kotlin
@Query("SELECT * FROM episodes ORDER BY created_at DESC LIMIT :limit OFFSET :offset")
fun findRecent(@Param("limit") limit: Int, @Param("offset") offset: Long): List<Episode>
```

**Violation (returning `Page<T>` directly from the controller):**
```kotlin
@GetMapping
fun list(pageable: Pageable): Page<Episode> = service.findAll(pageable) // leaks Spring's Page shape to clients
```

**Correct:**
```kotlin
// Repository
interface EpisodeRepository : CrudRepository<Episode, Long>, PagingAndSortingRepository<Episode, Long> {
    fun findByPodcastId(podcastId: String, pageable: Pageable): Page<Episode>
    fun findByPodcastIdAndStatusIn(podcastId: String, statuses: Collection<EpisodeStatus>, pageable: Pageable): Page<Episode>
}

// Controller
@GetMapping
fun list(
    @PathVariable podcastId: String,
    @RequestParam(required = false) status: List<String>?,
    @RequestParam(required = false, defaultValue = "0") page: Int,
    @RequestParam(required = false, defaultValue = "20") pageSize: Int
): ResponseEntity<Any> {
    if (page < 0) return ResponseEntity.badRequest().body(mapOf("error" to "page must be >= 0"))
    if (pageSize < 1 || pageSize > 200) return ResponseEntity.badRequest().body(mapOf("error" to "pageSize must be in [1, 200]"))
    val pageable = PageRequest.of(page, pageSize, Sort.by(Sort.Direction.DESC, "generatedAt", "id"))
    val result = episodeService.findByPodcastIdPaged(podcastId, parsedStatuses, pageable)
    return ResponseEntity.ok(result.toResponse { it.toResponse() })
}
```

**Cross-aggregate filtering**: when the natural filter lives on a different aggregate (e.g. paging publications by `podcast_id` which lives on `episodes`), resolve the foreign IDs first in the service and then use a derived `findByForeignKeyIn(ids, Pageable)` method. This keeps the repository surface clean and avoids hand-rolled JOIN queries with `@Query`.

**Dialect note (SQLite only)**: Spring Data JDBC has no built-in SQLite dialect. The project's `SqliteDialectConfig` extends `AbstractJdbcConfiguration` and delegates `jdbcDialect()` to `MySqlDialect` (whose `LIMIT n OFFSET m` syntax SQLite accepts). Do NOT delegate to `AnsiDialect` — its `SelectRenderContext` hard-codes `OFFSET n ROWS FETCH FIRST m ROWS ONLY`, which SQLite rejects, and the issue is invisible until the first `Page<T>` query is exercised.

**Exception:** Manual `LIMIT`/`OFFSET` is acceptable inside complex `@Query` methods that are already justified by Rule 11 (JOINs, optional filters).

---

## Rule 13: Use `Sort` Parameter Over Hardcoded Sort Order

When a repository query needs to support multiple sort directions, accept a `Sort` parameter instead of creating separate methods for each direction.

**Violation:**
```kotlin
fun findByPodcastIdOrderByCreatedAtAsc(podcastId: Long): List<EpisodeEntity>
fun findByPodcastIdOrderByCreatedAtDesc(podcastId: Long): List<EpisodeEntity>
```

**Correct:**
```kotlin
fun findByPodcastId(podcastId: Long, sort: Sort): List<EpisodeEntity>
```

A single hardcoded sort order is fine when only one direction is ever needed.

---

## Rule 14: Prefer Kotlin Nullability Over Optional

Repository methods should use Kotlin nullable return types (`T?`) instead of `Optional<T>`. Spring Data JDBC supports Kotlin nullability natively. Use `findByIdOrNull()` (from `org.springframework.data.repository.findByIdOrNull`) instead of `findById().orElse(null)`.

**Violation:**
```kotlin
val episode = episodeRepository.findById(id).orElse(null)
```

**Correct:**
```kotlin
val episode = episodeRepository.findByIdOrNull(id)
```

For custom query methods, declare nullable return types directly:

**Violation:**
```kotlin
@Query("SELECT * FROM episode WHERE podcast_id = :podcastId AND status = 'PUBLISHED' ORDER BY generated_at DESC LIMIT 1")
fun findLatestPublished(@Param("podcastId") podcastId: String): Optional<EpisodeEntity>
```

**Correct:**
```kotlin
@Query("SELECT * FROM episode WHERE podcast_id = :podcastId AND status = 'PUBLISHED' ORDER BY generated_at DESC LIMIT 1")
fun findLatestPublished(@Param("podcastId") podcastId: String): EpisodeEntity?
```

---

## Rule 15: Data Access Through Repositories Only

All database queries must go through Spring Data repositories, not raw `JdbcClient` or `JdbcTemplate` in services. For complex queries (JOINs, custom projections), use `@Query` on a repository method.

**Violation:**
```kotlin
@Service
class EpisodeService(private val jdbcTemplate: JdbcTemplate) {
    fun findRecent(): List<Episode> {
        return jdbcTemplate.query("SELECT * FROM episode ORDER BY created_at DESC LIMIT 10") { rs, _ ->
            // manual row mapping
        }
    }
}
```

**Correct:**
```kotlin
interface EpisodeRepository : CrudRepository<EpisodeEntity, Long> {
    @Query("SELECT * FROM episode ORDER BY created_at DESC LIMIT 10")
    fun findRecent(): List<EpisodeEntity>
}
```