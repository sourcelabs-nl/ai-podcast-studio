## 1. Search query

- [x] 1.1 Add an `EpisodeSearchRepositoryCustom` interface and `JdbcClient`-based implementation, following the `EpisodeArticleRepositoryCustomImpl` pattern
- [x] 1.2 Write the paged search query: match AND-ed terms case-insensitively across `episode_articles.topic` and `articles.title` (covered stories only, `topic_order IS NOT NULL`) and the episode's `script_text`, `recap`, `show_notes`
- [x] 1.3 Return matching episodes ordered `generated_at DESC, id DESC`, honouring the status filter, page, and page size, with a matching-row count for `total`
- [x] 1.4 Aggregate per-episode match details (distinct matching topics, matching article titles, each capped at 5) in the same round trip
- [x] 1.5 Escape `%` and `_` in the user's terms so they are matched literally rather than as `LIKE` wildcards

## 2. Service and controller

- [x] 2.1 Add a service method that takes the podcast, statuses, trimmed query, and pageable, and routes to the search query when the query is 2+ characters and to the existing paged lookup otherwise
- [x] 2.2 Add the optional `q` parameter to `EpisodeController.list` and pass it through, keeping all existing validation intact
- [x] 2.3 Add the `matches` object to the episode list response, present only when a search was applied

## 3. Frontend

- [x] 3.1 Add a debounced search input above the episode list, URL-synced as `q` via the existing `updateQuery` helper, resetting `page` to 0 on change and offering a clear control
- [x] 3.2 Include `q` in the episodes query string so search, status, and paging apply together
- [x] 3.3 Render match details under each row: matching topic labels, matching article titles, a distinct label for a script-only match, and an indication when the list was capped
- [x] 3.4 Show the match count when a search is active, and an empty state naming the query when nothing matched

## 4. Tests

- [x] 4.1 Repository tests: topic match, article-title match, script-only match, uncovered article excluded, case-insensitivity, all-terms-must-match, wildcard characters treated literally
- [x] 4.2 Repository tests: paging and `total` over a matching subset, and search combined with a status filter
- [x] 4.3 Controller tests: `q` passed through, blank and single-character `q` ignored, `matches` absent without a search
- [x] 4.4 Run `mvn test` and confirm the full suite passes
- [x] 4.5 Run `npx tsc --noEmit` in `frontend/` to typecheck without clobbering the dev server's build output

## 5. Verification

- [x] 5.1 Restart the application and exercise the endpoint against the real archive for a known topic, confirming the matched episodes and their match details
- [ ] 5.2 Check the episodes screen in the browser: search, clear, combine with a status filter, reload to confirm the URL round-trips
