## Why

`nitter.net` was decommissioned and no public Nitter instance survives, so the 35 X accounts the podcast follows had to move to Mastodon-based X mirrors (`zpravobot.news`) and a third-party X-to-RSS service. Mastodon RSS items carry no `<title>` element at all: a post has content and nothing else. The fetcher dropped any entry without a title, so a repointed source parsed its feed, discarded all 20 items, and logged "Fetched 0 new entries" while appearing healthy.

## What Changes

- The RSS fetcher derives a title from the entry body when the entry has no `<title>` (or a blank one), instead of dropping the entry.
- A derived title is the first sentence when there is a clean break within 100 characters, otherwise the body truncated on a word boundary and ellipsized.
- An entry with neither a title nor a body is still dropped, since it carries nothing to store.
- An explicit non-blank title is still preferred over the body, so existing feeds are unaffected.
- No breaking changes.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `source-polling`: the "RSS/Atom feed polling" requirement gains title derivation, so entries from feeds that omit `<title>` are stored rather than silently discarded.

## Impact

- `src/main/kotlin/com/aisummarypodcast/source/RssFeedFetcher.kt`: title resolution moves after body extraction (the body is needed to derive from) and gains a `deriveTitle` helper.
- `src/test/kotlin/com/aisummarypodcast/source/RssFeedFetcherTest.kt`: covers a titleless Mastodon-shaped entry, long-body truncation, the title-less-and-bodyless case, and explicit-title precedence.
- Enables the `zpravobot.news` X-mirror sources and any other Mastodon-derived feed. The X-to-RSS service in use does supply titles, so it does not depend on this.
