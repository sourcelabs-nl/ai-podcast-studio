## Why

Spotify renders the RSS `content:encoded` episode description as flat text, so the per-article links grouped under topic headings show up as a long, noisy wall of raw article/post titles. The listener-facing description should only name the topics discussed; the full article list is already available on the linked sources page.

## What Changes

- The RSS feed episode `content:encoded` "Topics Covered" section lists only the distinct topic names (plain `<li>` items, in topic order), no article titles or links.
- Legacy episodes without topic data no longer render a per-article "Sources:" list in `content:encoded`; they rely on the "view all sources and show notes" link instead.
- The show-notes paragraphs, the sources-page link sentence, and the contact footer are unchanged.
- The sources HTML page (`episode-sources-file` capability) is unchanged and keeps the full per-article list grouped by topic.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `podcast-feed`: The "Feed content:encoded shows topic-representative sources" requirement changes: topic names are listed as plain text without article links, and the legacy no-topic fallback drops the inline article list entirely.

## Impact

- `src/main/kotlin/com/aisummarypodcast/podcast/FeedGenerator.kt` (`buildHtmlDescription`)
- `src/test/kotlin/com/aisummarypodcast/podcast/FeedGeneratorTest.kt`
- No API, database, or configuration changes. Feeds regenerate with the new format on next publish.
