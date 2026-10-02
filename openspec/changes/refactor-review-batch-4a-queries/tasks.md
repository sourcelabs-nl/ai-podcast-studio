# Tasks

## 1. Episode queries

- [x] 1.1 Replace the in-memory work in `cleanupOldEpisodes`, `countArticles` and `findLinkedArticlesAndTopics` with repository queries, and verify with repository tests (old and new rows, other podcast) and the existing service tests
- [x] 1.2 Push the status, purpose and focus conditions of `hasActiveEpisode` and `findRecentFocusEpisodes` into queries, and verify with repository tests covering experiment episodes, focus and regular episodes and the status set

## 2. Publications and sources

- [x] 2.1 Make `PublishingService.listByPodcast` page in the database, and verify with a repository or service test that the order, page content and total equal the old behavior for a podcast with several episodes and publications
- [x] 2.2 Use the existing enabled-source queries in `SourceService.findSourcesBehindWindow` and `getHostBreakerStates`, and verify with the existing tests plus one asserting disabled sources are excluded

## 3. Articles

- [x] 3.1 Turn `findUnscoredBySourceIds` and `findAllSince` into derived queries, and verify with repository tests against SQLite

## 4. Completion

- [x] 4.1 Run `mvn test` and restart the app
