## Why

Adding `https://openai.com/news/rss.xml` (1153 entries) as a source never completed: the create request ran for over 12 minutes, held two Tomcat request threads, and had to be cleared by restarting the application. The feed's entire archive was being deep-fetched, one linked article at a time, on the request thread.

Two independent paths caused it. Source creation validates a feed by fetching it with deep-fetch enabled, though validation only needs the feed to parse and yield one item. And `SourcePoller` applied its max-article-age and pre-creation bounds in the save loop, after `RssFeedFetcher` had already deep-fetched every entry it returned, so a large archive was crawled in full and then discarded.

## What Changes

- Source validation fetches with deep-fetch disabled, so creating a source no longer crawls the linked articles of the feed it is validating.
- `SourcePoller` computes the earliest publish time worth fetching (the latest of `lastSeenId`, the max-article-age cutoff, and, on a first poll, the source's creation time) and passes it to the fetcher, so entries outside the bounds are filtered before any deep-fetch.
- The bounds continue to be applied in the save loop, which still covers the `TWITTER` and `WEBSITE` paths that do not go through the RSS entry filter.
- No breaking changes.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `article-deep-fetch`: deep-fetch is now bounded by the poll's time window and excluded from source validation, rather than being attempted for every entry a feed returns.

## Impact

- `src/main/kotlin/com/aisummarypodcast/source/SourcePoller.kt`: age/creation bounds move above the fetch, and a new `rssFetchFloor` helper folds them into the value passed as `lastSeenId`.
- `src/main/kotlin/com/aisummarypodcast/source/SourceService.kt`: `validateRssUrl` passes `deepFetch = false`.
- `src/test/kotlin/com/aisummarypodcast/source/SourcePollerTest.kt`: three tests pinning which bound reaches the fetcher; the existing category-filter test no longer asserts a pass-through `lastSeenId`.
- `src/test/kotlin/com/aisummarypodcast/source/SourceServiceTest.kt`: five validation stubs updated for the new argument.
- `RssFeedFetcher.fetch` keeps its existing signature: the floor is the same lower time bound the fetcher already applies as `lastSeenId`, so no parameter was added.
