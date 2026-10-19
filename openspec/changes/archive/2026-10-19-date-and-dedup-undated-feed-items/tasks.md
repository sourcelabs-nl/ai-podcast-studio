# Tasks

## 1. Polling

- [x] 1.1 `SourcePoller` dates an undated item with its first-seen moment and skips a known URL (except website sources); `PostRepository.existsBySourceIdAndUrl`. Verified by `SourcePollerTest` (undated, dated, known URL skipped, website kept); `mvn test` passes (2025 tests).

## 2. Data

- [x] 2.1 Migration V88 backfills undated posts and articles and indexes `posts(source_id, url)`. Verified by `UndatedPostBackfillMigrationTest`.
- [x] 2.2 Restart and confirm on the production database that no undated post remains and episode 297's undated articles now carry their first-seen dates.
