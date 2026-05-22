## 1. Pagination plumbing

- [x] 1.1 Add `PagedResponse<T>` data class to `PodcastDtos`
- [x] 1.2 Add `Page<T>.toResponse()` and `Page<T>.toResponse(mapper)` extensions to `PodcastMappers`
- [x] 1.3 Fix `SqliteDialectConfig` to extend `AbstractJdbcConfiguration` and delegate to `MySqlDialect`

## 2. Episodes endpoint

- [x] 2.1 Add `findByPodcastId(podcastId, Pageable): Page<Episode>` and `findByPodcastIdAndStatusIn(podcastId, Collection<EpisodeStatus>, Pageable): Page<Episode>` to `EpisodeRepository`, extending `PagingAndSortingRepository`
- [x] 2.2 Add `EpisodeService.findByPodcastIdPaged(podcastId, statuses, pageable): Page<Episode>`
- [x] 2.3 Rewrite `EpisodeController.list` to accept `page`, `pageSize`, repeatable `status`; validate ranges; return `PagedResponse<EpisodeResponse>`

## 3. Publications endpoint

- [x] 3.1 Add `EpisodePublicationRepository.findByEpisodeIdIn(Collection<Long>, Pageable): Page<EpisodePublication>` derived method
- [x] 3.2 Add `PublicationEpisodeRef` and `PodcastPublicationRow` DTOs to `PublishingDtos`
- [x] 3.3 Add `PublishingService.listByPodcast(podcastId, Pageable): Page<PodcastPublicationRow>` resolving episode IDs upfront
- [x] 3.4 Add `PodcastPublicationsController` exposing `GET /users/{userId}/podcasts/{podcastId}/publications`

## 4. Tests

- [x] 4.1 Update `EpisodeControllerTest` for the new envelope shape; add cases for multi-status, page bounds
- [x] 4.2 Add `EpisodeRepositoryPagingTest` (4 cases) covering paged finders, multi-status filter, publications cross-podcast scoping, and split pagination
- [x] 4.3 `mvn test` — all 842 backend tests pass

## 5. Frontend

- [x] 5.1 Add `PagedResponse<T>`, `PublicationEpisodeRef`, `PodcastPublicationRow` types
- [x] 5.2 Create `components/paginator.tsx` (page-size Select + Previous/Next + from-to-of-total)
- [x] 5.3 Episodes tab: URL-synced `page` / `pageSize` / `status[]`, multi-select status dropdown, paginator below table
- [x] 5.4 `PublicationsTab` rewired to the new aggregated endpoint with its own paginator; keep an optional `episodeId` prop for the single-episode reuse on the episode detail page
- [x] 5.5 `tsc --noEmit` clean

## 6. Pattern documentation

- [x] 6.1 Expand `spring-data-jdbc` skill Rule 12 with the codified pagination pattern, controller-side `PagedResponse` mapping, and the SQLite dialect note

## 7. Smoke test

- [x] 7.1 Restart app; verify `GET .../episodes?page=0&pageSize=3` returns envelope with `total=109`
- [x] 7.2 Verify `?status=GENERATED&status=FAILED` narrows total to 54
- [x] 7.3 Verify `GET .../publications` returns 96 rows total with episode metadata embedded
