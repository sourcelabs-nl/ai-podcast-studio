## 1. Idempotent aggregation writes

- [x] 1.1 Add `PostArticleRepositoryCustom` with `linkIfAbsent(postId, articleId)` backed by
      `INSERT OR IGNORE`, and let `PostArticleRepository` extend it. Verify by calling it twice for
      the same pair in an integration test and asserting a single link remains.
- [x] 1.2 Use `linkIfAbsent` in `SourceAggregator.aggregateAndPersist`, and resolve a duplicate
      article on `(source_id, content_hash)` by re-reading the stored row instead of failing.
      Verify with `mvn test -Dtest=SourceAggregatorTest,ArticlePostThreadTest`.

## 2. Dedup degeneracy guard

- [x] 2.1 Add the validation to `TopicDedupFilter`: a `NEW` cluster selecting no article is invalid;
      reject the response when the majority of `NEW` clusters are empty, drop a minority with a WARN.
      Applies to a strictly parsed response as well as a salvaged one. Verify with unit tests
      covering 33-of-34 empty (rejected), 2-of-30 empty (dropped, rest kept), and all-`CONTINUATION`
      empty (accepted).
- [x] 2.2 Confirm the rejection raises inside the retried block so the escalating-prompt retry
      applies and an exhausted retry fails the episode. Verified by configuration rather than a new
      test: `topic-dedup` inherits `external-api` with no `retry-exceptions` list, so every
      exception is retried, and the 2026-09-10 log shows the retry firing on the identical
      `IllegalStateException` the salvage floor raises from the same method.

## 3. Auto-publish floor

- [x] 3.1 Add `min-articles` to the publishing properties in `AppProperties` and
      `application.yaml`, defaulting to 5. Verify the property binds by asserting the default in a
      context test.
- [x] 3.2 Gate `AutoPublishListener` on `episodeService.countArticles(episodeId)` against the floor,
      skipping with a WARN that names the episode and its article count. Verify with tests for below
      the floor (no publish, episode stays `GENERATED`), at the floor (publishes), and that
      `PublishingService.publish` is untouched so a manual publish still works.

## 4. Verification

- [x] 4.1 Run `mvn test` and confirm the full suite passes.
- [x] 4.2 Run the code review and fix violations. Found one real violation: `saveOrFindArticle`
      caught `DataIntegrityViolationException`, which SQLite never produces here (constraint
      failures arrive as `UncategorizedSQLException` with error code 19), making the recovery
      unreachable. Fixed by extracting `isConstraintViolation` to `util/SqliteErrors.kt` and using
      it from both `SourceAggregator` and `CachingChatModel`, with two regression tests that fail
      against the old catch.
- [ ] 4.3 Restart the app (`./stop.sh` then `./start.sh`) and regenerate the episode, confirming
      that a healthy dedup response still composes a full episode and that the run is not blocked by
      the new guards.
