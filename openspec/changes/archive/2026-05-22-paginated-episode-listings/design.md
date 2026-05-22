## Context

Both lists on the podcast detail page (Episodes, Publications) loaded the full dataset on every render. The episodes table had 109 rows for one of the active podcasts; the publications tab fanned out a per-episode `Promise.all` returning ~96 rows in aggregate. The Episodes status filter was a single-select dropdown wired to `?status=X` on the backend, but users routinely want to view "everything that's not DISCARDED" or "GENERATED + FAILED" together.

This is the first time the project introduces paginated list endpoints, so the change also has to set the canonical pattern for future paged endpoints.

## Goals / Non-Goals

**Goals:**
- Server-side pagination for both Episodes and Publications, configurable per podcast detail page.
- Multi-select status filter on the Episodes tab, server-side.
- Establish `PagedResponse<T>` as the API envelope and a documented Spring Data JDBC `Page<T>` pattern so future paged endpoints don't reinvent it.

**Non-Goals:**
- Multi-select filtering for the Publications tab (deferred — out of scope for this change).
- Sortable column headers (the Episodes tab keeps `generatedAt DESC, id DESC`; Publications keeps `createdAt DESC, id DESC`).
- Cursor/keyset pagination (offset is fine at current data volumes; can revisit if/when episode tables grow past tens of thousands).

## Decisions

**`PagedResponse<T>` envelope vs. returning Spring's `Page<T>`.**
Spring's `Page<T>` serializes with `content`, `pageable`, `sort`, `last`, `numberOfElements`, etc., which leaks the framework type and is awkward to type on the frontend. The envelope is five flat fields (`items`, `page`, `pageSize`, `total`, `totalPages`) and is stable independent of Spring Data version bumps. Centralised mappers `Page.toResponse()` / `Page.toResponse { mapper }` convert at the controller boundary.

**Derived `Page<T>` repository methods over custom impl.**
Spring Data JDBC's `PagingAndSortingRepository` handles derived methods returning `Page<T>` natively, including the COUNT(*) query. Initial implementation used custom `<Name>Impl` fragments with hand-rolled `NamedParameterJdbcTemplate` + `PageImpl`, but that's exactly what Spring Data exists to avoid. Reverted to derived methods.

**Cross-aggregate filter via two-step ID resolution, not JOIN.**
The Publications tab needs to filter by `podcasts.id`, but that column lives on `episodes`, not on `episode_publications`. Options were (a) a `@Query` with a JOIN, (b) a custom repository fragment, or (c) resolve episode IDs first in the service, then use a derived `findByEpisodeIdIn(ids, Pageable)`. Picked (c): keeps the repository surface a clean derived method, sorting works on the publication's own `createdAt`, and episodes-per-podcast is bounded enough that loading IDs up front is cheap (`EpisodeRepository.findByPodcastId` already gets called on every dashboard render). Sorting by publication `createdAt` is arguably more correct anyway — users browsing publications expect "newest first" by publication time, not by source-episode time.

**Status filter changes from single string to `List<String>` on the controller.**
Spring binds repeated `?status=` params into `List<String>` automatically. A single `?status=X` continues to bind as a single-element list. The service-layer signature changes from `EpisodeStatus?` to `Collection<EpisodeStatus>` (empty meaning "no filter"). The repository gains `findByPodcastIdAndStatusIn(podcastId, statuses, Pageable)` derived method.

**SQLite dialect fix.**
The hidden defect: `SqliteDialectConfig` previously registered a standalone `@Bean jdbcDialect()` that delegated to `AnsiDialect.INSTANCE`. That bean was shadowed by Spring Boot's `JdbcRepositoriesAutoConfiguration` because the auto-config registers its own `jdbcDialect` via `SpringBootJdbcConfiguration extends AbstractJdbcConfiguration`, gated on `@ConditionalOnMissingBean(AbstractJdbcConfiguration.class)`. A standalone `@Bean` doesn't satisfy that condition, so both beans existed and the autoconfig one won. Also, even when our bean did get used, delegating to `AnsiDialect` was still wrong: its `SelectRenderContext` emits `OFFSET ... FETCH FIRST ... ROWS ONLY` independent of `LimitClause`, and SQLite parses that as a syntax error.

Fix in two parts:
1. `SqliteDialectConfig` now `extends AbstractJdbcConfiguration` and `override`s `jdbcDialect(NamedParameterJdbcOperations)`. This makes the auto-config's `@ConditionalOnMissingBean` skip.
2. The dialect delegates to `MySqlDialect.INSTANCE` (not `AnsiDialect`). MySQL's `LimitClause` and `SelectRenderContext` both speak `LIMIT n OFFSET m`, which SQLite accepts.

This kind of bug would have stayed dormant until the first `Page<T>` query was exercised in production. Documented in the skill so future contributors know.

**Frontend: URL-synced pagination state on the Episodes tab; component state on Publications.**
Episodes already syncs the tab name to the URL, so adding `page`/`pageSize`/`status` makes deep links (and the browser back button) work — useful when an admin is sharing a link to "all FAILED episodes". Publications doesn't pre-existing URL state and lives inside a tab anyway, so component state is fine; reloading the page resets to page 0.

**Backwards-compat hack: `PublicationsTab` accepts an optional `episodeId`.**
The single-episode page (`/episodes/{id}`) reuses `PublicationsTab` to show that one episode's publications. Rather than building a second component, the tab gained an optional `episodeId` prop: when set, it calls the existing per-episode endpoint (no pagination needed for 1-3 rows) and renders without the paginator. When unset, it calls the new podcast-level endpoint with pagination. Pragmatic, single component, no duplicate code.

## Risks / Trade-offs

- **[Risk] Existing API consumers break on the new envelope shape.** → Only the dashboard frontend consumes the episodes endpoint today, and it's updated in lockstep. Document the change in the OpenSpec delta so any future external consumer hits the spec first.
- **[Risk] The two-step publication resolution can produce a stale episode list if episodes are added between the two queries.** → Acceptable. The worst case is a publication briefly missing from the page; next refresh corrects it. Race window is sub-millisecond.
- **[Risk] Offset pagination has well-known performance issues past tens of thousands of rows.** → Not a current concern at 109 episodes. If we ever cross that threshold, switch to keyset pagination on `(generatedAt, id)` and the API envelope stays the same.
- **[Trade-off] Spring Data's `Page<T>` runs the COUNT query on every page load.** → Acceptable for the current data volume. The COUNT cost is bounded by the WHERE clause selectivity, which for `podcast_id = ?` is one B-tree lookup per podcast.

## Migration Plan

1. Deploy backend; the new envelope replaces the old array response on the episodes endpoint.
2. Deploy frontend; both tabs use pagination immediately.
3. No DB migration needed.
4. Rollback: revert both deployments together; previous frontend talks to previous backend without issue.
