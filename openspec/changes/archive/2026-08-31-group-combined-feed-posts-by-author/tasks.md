## 1. Resolve an author per post

- [x] 1.1 Add `internal fun resolveAuthorKey(post: Post): String?` to `SourceAggregator`
- [x] 1.2 Resolve from an `x.com`/`twitter.com` `/<handle>/status/<id>` URL path first
- [x] 1.3 Fall back to a leading `@handle:` title prefix, then to the `author` field
- [x] 1.4 Document why the URL is the most reliable of the three on a combined feed

## 2. Group by author before threading

- [x] 2.1 Group posts by author key in `aggregatePosts` and thread each group separately
- [x] 2.2 Keep posts with no resolvable author in one group, preserving today's behaviour
- [x] 2.3 Log the author count alongside the existing thread count
- [x] 2.4 Document that a source is no longer the same thing as an account

## 3. Detect Narro replies at ingestion

- [x] 3.1 Add `NarroFeed.replyTarget(rawHtml): String?` reading the `narro-reply-header` handle
- [x] 3.2 Strip markup from entry titles in `RssFeedFetcher` with the body's Jsoup text extraction
- [x] 3.3 Normalise a marked reply's title to `R to @<handle>: `, not double-prefixing
- [x] 3.4 Verify the marker survives Rome's entity unescaping using a real feed item

## 4. Tests

- [x] 4.1 `SourceAggregatorTest`: posts are grouped by author before threading
- [x] 4.2 `SourceAggregatorTest`: a reply never attaches across authors
- [x] 4.3 `SourceAggregatorTest`: author resolved from URL, title prefix and author field
- [x] 4.4 `SourceAggregatorTest`: posts with no resolvable author form one group
- [x] 4.5 `SourceAggregatorTest`: a single-account feed threads exactly as before
- [x] 4.6 `RssFeedFetcherTest`: markup stripped from an entry title
- [x] 4.7 `RssFeedFetcherTest`: a Narro reply title is normalised to `R to @`
- [x] 4.8 `RssFeedFetcherTest`: a non-reply entry is not prefixed, and an already-prefixed one is not doubled
- [x] 4.9 Run `mvn test` and confirm the whole suite passes (1249 tests, 0 failures)

## 5. Enable and verify on the real source

- [x] 5.1 Set `aggregate = true` on the `X via Narro` source via the API
- [x] 5.2 Restart the app, poll the source, and confirm the log reports posts grouped into authors and threads (`Grouped 476 posts into 178 threads across 14 author(s)`)
- [x] 5.3 Confirm the resulting article count per author matches the feed's distribution (476 posts → 178 articles; existing posts were backfilled with the ingestion-time title normalisation so their reply chains thread)
