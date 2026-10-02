# Proposal

## Why

The code review of 2026-10-01 found places that load rows and then filter, sort or count them in memory although the repository can answer the question: episode cleanup, episode and article counts, focus-episode and active-episode lookups, per-article lookups in a loop, the publication listing and two enabled-source filters. The sets are small today, but each is a full read per call and the pattern invites the next one.

## What Changes

- `EpisodeService.cleanupOldEpisodes` selects the old episodes in the database.
- `EpisodeService.countArticles` counts in the database instead of loading the links.
- `EpisodeService.findLinkedArticlesAndTopics` loads the articles in one query instead of one per link.
- `EpisodeService.hasActiveEpisode` and `findRecentFocusEpisodes` push their status, purpose and focus conditions into derived queries.
- `PublishingService.listByPodcast` pages in the database instead of loading every episode of the podcast.
- `SourceService.findSourcesBehindWindow` and `getHostBreakerStates` use the existing enabled-source queries.
- `ArticleRepository.findUnscoredBySourceIds` and `findAllSince` become derived queries.

The other `@Query` methods stay: their derived names would be less readable than the SQL (`is_processed`, `NULLS LAST`, `LIMIT`) and the codebase accepts that. Results are unchanged.

## Capabilities

### New Capabilities

### Modified Capabilities

## Impact

- `podcast/EpisodeService.kt`, `publishing/PublishingService.kt`, `source/SourceService.kt`, and the repositories they use (`EpisodeRepository`, `EpisodeArticleRepository`, `ArticleRepository`, `EpisodePublicationRepository`).
- Repository tests against SQLite for each new query; service tests keep passing unchanged where they mock the repositories, and are adjusted where they stub a replaced method.
