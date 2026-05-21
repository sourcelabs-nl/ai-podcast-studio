## 1. Thread Detection and Aggregation

- [x] 1.1 Add `groupPostsByThread()` method to `SourceAggregator` that sorts posts by `publishedAt` ascending and groups them into threads: non-reply posts start a new thread, reply posts (title starts with "R to @") attach to the current thread
- [x] 1.2 Add `rewriteNitterUrl()` helper that replaces `nitter.net` with `x.com` in a URL hostname
- [x] 1.3 Rewrite `aggregatePosts()` to use thread-based grouping: call `groupPostsByThread()`, then create one article per thread with parent post URL (rewritten via `rewriteNitterUrl()`), parent post title, and combined body
- [x] 1.4 Update `post_articles` linking in `aggregateAndPersist()` to link each post to its thread's article (instead of all posts to one article)
- [x] 1.5 Add tests for thread detection: single thread, multiple threads, orphan replies, standalone posts, mixed
- [x] 1.6 Add tests for nitter→x.com URL rewriting
- [x] 1.7 Add tests for thread-based article creation: verify title, URL, body, and post-article links

## 2. Title Truncation in Sources HTML

- [x] 2.1 Add title truncation logic in `EpisodeSourcesGenerator`: truncate titles longer than 120 characters with "..." when rendering link text
- [x] 2.2 Add tests for title truncation (long title truncated, short title unchanged)

## 3. Backfill Last Episode

- [x] 3.1 Update aggregated article URLs for episode 77: rewrite nitter.net/user/rss to x.com/user profile URLs, and rewrite remaining nitter.net post URLs to x.com
- [x] 3.2 (Simplified) Full re-aggregation skipped: would require re-running the LLM pipeline. URL fix is sufficient for the visual improvement
- [x] 3.3 (Simplified) No re-linking needed: existing episode_articles links preserved with corrected URLs
- [x] 3.4 Regenerate sources HTML for episode 77 and verify the result

## 4. Verification

- [x] 4.1 Run `mvn test` to verify all existing and new tests pass
