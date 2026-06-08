## 1. Feed description simplification (retrofit: already implemented)

- [x] 1.1 Replace per-article links in `FeedGenerator.buildHtmlDescription` "Topics Covered" with a plain `<ul>` of distinct topic names in topic order
- [x] 1.2 Drop the inline "Sources:" article list for legacy episodes without topic data (sources-page link covers it)
- [x] 1.3 Update `FeedGeneratorTest` to assert topic names only, no article titles/links, and the legacy fallback omitting the list
- [x] 1.4 Run `mvn test` and verify the full suite passes
