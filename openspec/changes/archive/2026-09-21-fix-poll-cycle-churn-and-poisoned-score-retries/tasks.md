<!-- Implemented before this change was written; every task below is already done. -->

## 1. Post age measured on the published date

- [x] 1.1 `PostRepository.findUnlinkedBySourceIds`: filter on `COALESCE(p.published_at, p.created_at) >= :cutoff`
- [x] 1.2 `PostRepository.findUnlinkedSince`: same change against `:since`
- [x] 1.3 `PostRepository.deleteOldUnlinkedPosts`: filter on `COALESCE(published_at, created_at) < :cutoff`
- [x] 1.4 Document on `findUnlinkedBySourceIds` why the column matters, naming the aggregate/delete/re-aggregate loop it prevents

## 2. Distinct prompt per scoring attempt

- [x] 2.1 Add `internal fun promptForAttempt(prompt: String, attempt: Int)` to `ArticleScoreSummarizer`, returning the prompt unchanged on attempt 1
- [x] 2.2 Add `jsonOnlyCorrection(attempt: Int)` naming the attempt and demanding raw JSON with no prose or code fences
- [x] 2.3 Route the retry loop's `.user(...)` call through `promptForAttempt`
- [x] 2.4 Document why an identical retry prompt is a bug, covering the cache replay and the null-`relevanceScore` re-queue

## 3. Tolerate a lost optimistic-lock race on the source row

- [x] 3.1 Add `private fun saveSourceState(source: Source, update: (Source) -> Source)` to `SourcePoller`, catching `OptimisticLockingFailureException`, re-reading via `findByIdOrNull`, and reapplying `update` once
- [x] 3.2 Return without saving when the re-read finds the source deleted, logging at WARN
- [x] 3.3 Route the success-path save through the helper
- [x] 3.4 Route the failure-path save through the helper, deriving `consecutiveFailures` and the `maxFailures` threshold from the row passed in rather than the stale copy

## 4. Tests

- [x] 4.1 `PostRepositoryTest`: add a `publishedAt` parameter to the `post` helper
- [x] 4.2 `PostRepositoryTest`: a recently ingested, back-dated post is excluded from `findUnlinkedBySourceIds`
- [x] 4.3 `PostRepositoryTest`: a recently published post ingested long ago is still returned
- [x] 4.4 `PostRepositoryTest`: `deleteOldUnlinkedPosts` deletes the back-dated post and retains the recently published one
- [x] 4.5 `ArticleScoreSummarizerTest`: attempt 1 is unchanged, attempt 2 carries the correction, and all three attempts are distinct
- [x] 4.6 `ArticleScoreSummarizerTest`: an always-failing call sends three distinct prompts
- [x] 4.7 `SourcePollerTest`: a lock failure on a successful poll retries against the re-read source and records neither a failure nor a disable
- [x] 4.8 `SourcePollerTest`: a lock failure with the source gone saves once and does not throw
- [x] 4.9 Run `mvn test` and confirm the whole suite passes (1164 tests, 0 failures)

## 5. Verification against the running application

- [x] 5.1 Confirm the stale post backlog clears on the first cleanup pass after restart: 13,056 posts to 12,636, 424 unlinked to 4, and the churning feed's 753 posts down to the 393 that hold articles
- [x] 5.2 Confirm a full poll round (12 sources) emits no `[Aggregator]` line at all, where every previous round emitted `Created 360 articles from 360 posts`, and that `MAX(articles.id)` stops advancing
- [x] 5.3 Confirm eager scoring drops from 380 articles per round to 9
- [x] 5.4 Confirm each of the 9 previously unscorable articles logs one `Retry 1/3` (the cached unparseable response) and then succeeds, leaving zero `Error scoring/summarizing` lines and a persisted `relevanceScore` on all 9
- [x] 5.5 Confirm the round produces no ERROR lines at all, against 33 in a comparable window before the change
- [ ] 5.6 The optimistic-lock path is not exercised live — it needs two overlapping polls of one source, which did not occur in the observation window. Covered by unit tests on both branches only.
