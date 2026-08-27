> Retrofitted change: the implementation landed before this change was written, so every task is
> already complete.

## 1. Fetcher

- [x] 1.1 Move title resolution in `RssFeedFetcher.fetch` to after body extraction, so the body is available to derive from
- [x] 1.2 Prefer `entry.title` only when non-blank, falling back to a derived title, and drop the entry only when neither a title nor a body exists
- [x] 1.3 Add the `deriveTitle` helper: first sentence within the length cap, else word-boundary truncation with an ellipsis
- [x] 1.4 Add `DERIVED_TITLE_MAX_LENGTH`, `MIN_DERIVED_TITLE_LENGTH` and the whitespace-collapsing regex as companion constants
- [x] 1.5 Document on the helper why titleless entries occur and which sources depend on this

## 2. Tests

- [x] 2.1 Titleless Mastodon-shaped entry yields a title derived from its body
- [x] 2.2 Long titleless body is truncated on a word boundary, ellipsized, and stays a prefix of the body
- [x] 2.3 Entry with neither title nor body is skipped
- [x] 2.4 Explicit title takes precedence over the body

## 3. Verification

- [x] 3.1 Run `mvn test` and confirm the full suite passes
- [x] 3.2 Restart the application and confirm the repointed X-mirror sources ingest entries (11, 17, 9 and 3 new entries on the first poll after the fix)
- [x] 3.3 Confirm derived titles read sensibly in the scoring log
