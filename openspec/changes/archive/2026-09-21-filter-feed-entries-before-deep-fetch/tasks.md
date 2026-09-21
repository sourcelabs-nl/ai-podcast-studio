> Retrofitted change: the implementation landed before this change was written, so every task is
> already complete.

## 1. Poller

- [x] 1.1 Move the max-article-age cutoff, first-poll flag and source creation time above the fetch dispatch in `SourcePoller.poll`
- [x] 1.2 Add the `rssFetchFloor` helper returning the latest of `lastSeenId`, the age cutoff, and (on a first poll) the creation time
- [x] 1.3 Pass the floor to the RSS fetch, and document why it goes in the `lastSeenId` slot rather than a new parameter
- [x] 1.4 Leave the save-loop bounds in place for the TWITTER and WEBSITE paths

## 2. Validation

- [x] 2.1 Pass `deepFetch = false` from `SourceService.validateRssUrl`, with a comment naming the feed that exposed the problem

## 3. Tests

- [x] 3.1 Age cutoff reaches the fetcher when there is no `lastSeenId`
- [x] 3.2 First poll passes the source creation time when it is the tightest bound
- [x] 3.3 `lastSeenId` wins when it is newer than the age cutoff
- [x] 3.4 Update the category-filter test, which asserted the old pass-through `lastSeenId`
- [x] 3.5 Update the five `SourceServiceTest` validation stubs for the new `deepFetch` argument

## 4. Verification

- [x] 4.1 Run `mvn test` and confirm the full suite passes
- [x] 4.2 Restart the application and add `openai.com/news/rss.xml` (1153 entries): HTTP 201 in 0.48s, versus a 12-minute hang before
- [x] 4.3 Confirm zero deep-fetch attempts against `openai.com` on the current instance, and that the first poll returned no entries
