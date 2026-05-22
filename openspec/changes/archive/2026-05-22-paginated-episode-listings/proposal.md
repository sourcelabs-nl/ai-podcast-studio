## Why

The podcast detail page (`/podcasts/{id}`) loads every episode and every publication into a single response. For an active daily podcast that's already at 109 episodes with 96 publication records, every page load streams hundreds of rows and renders one giant scrolling table, and the cost grows without bound. Server-side pagination is needed before the dashboard becomes unusable. The Episodes tab additionally needs multi-select status filtering so power users can slice across more than one state at a time (e.g. "all GENERATED and FAILED").

## What Changes

- Add server-side pagination to the episodes list endpoint (`GET /users/{userId}/podcasts/{podcastId}/episodes`):
  - New query params `page` (0-indexed, default 0) and `pageSize` (default 20, range 1-200).
  - **BREAKING (API shape)**: response changes from a JSON array to a `PagedResponse<EpisodeResponse>` envelope (`items`, `page`, `pageSize`, `total`, `totalPages`). All known callers updated in the same change.
  - Change `status` from a single value to a repeatable query param (`?status=GENERATED&status=FAILED`), bound as `List<String>` on the controller. A single `?status=X` continues to work.
- Add a new podcast-level publications endpoint:
  - `GET /users/{userId}/podcasts/{podcastId}/publications?page=N&pageSize=M`
  - Returns `PagedResponse<PodcastPublicationRow>` where each row carries the publication plus a lightweight episode reference (id, generatedAt, status).
  - Replaces the frontend's per-episode `Promise.all` fan-out on the Publications tab.
- Establish `PagedResponse<T>` as the canonical pagination envelope for the project and document the implementation pattern in the `spring-data-jdbc` skill (Rule 12): derived `Page<T>` methods on a `PagingAndSortingRepository`, controller maps to `PagedResponse<T>` so the framework type never leaks.
- Fix the SQLite dialect: `SqliteDialectConfig` now extends `AbstractJdbcConfiguration` and delegates to `MySqlDialect` instead of `AnsiDialect`. This is what makes `Page<T>` derived queries actually work on SQLite (`AnsiDialect.SelectRenderContext` hard-codes `OFFSET ... FETCH FIRST` syntax that SQLite rejects).
- Frontend:
  - New `Paginator` component (Previous/Next + "from-to of total" + page-size Select with 10/20/50/100).
  - Episodes tab gets multi-select status filtering (replaces single-select dropdown) and pagination, with `page`, `pageSize`, and `status[]` synced to the URL.
  - Publications tab fetches via the new aggregated endpoint and gets its own paginator.
  - `PublicationsTab` gains an optional `episodeId` prop so the single-episode publication list (episode detail page) keeps working without pagination.

## Capabilities

### New Capabilities
(none)

### Modified Capabilities
- `episode-detail-page`: episodes list endpoint changes shape and gains pagination/multi-status filter; UI gains paginator and multi-select.
- `episode-publishing`: new podcast-level paginated publications endpoint; publications tab UI rewired around it.

## Impact

- **API**: Episodes list response envelope is a breaking change for any external consumer. Internal frontend updated in lockstep.
- **Code**: `EpisodeRepository`, `EpisodeService.findByPodcastIdPaged`, `EpisodeController.list`, `PublishingService.listByPodcast`, new `PodcastPublicationsController`, `EpisodePublicationRepository.findByEpisodeIdIn`, `PodcastDtos.PagedResponse`, `PublishingDtos.PodcastPublicationRow`, `SqliteDialectConfig` (dialect fix), `Page.toResponse` mapper helpers.
- **Frontend**: `components/paginator.tsx` (new), `app/podcasts/[podcastId]/page.tsx`, `components/publications-tab.tsx`, `app/podcasts/[podcastId]/episodes/[episodeId]/page.tsx`, `lib/types.ts`.
- **Skills**: `spring-data-jdbc` Rule 12 expanded with the project's pagination pattern, plus the SQLite dialect note.
- **DB**: no schema changes.
- **Tests**: existing `EpisodeControllerTest` updated for the new envelope; new `EpisodeRepositoryPagingTest` covers paged + multi-status finders for episodes and publications. All 842 backend tests pass; `tsc --noEmit` clean on the frontend.
