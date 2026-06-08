## Why

Two episodes shipped broken feed output. The 2026-06-04 episode had all its articles stored with a blank topic label (`topic = ""`, `topicOrder = 0`), so the `content:encoded` "Topics Covered" section rendered a single empty `<li></li>` bullet that Spotify showed as nothing. The 2026-06-05 episode had no `showNotes` and no `recap`, so the description fell back to the raw script text, leaking the literal `<interviewer>` / `<expert>` speaker tags (and a "Writing the script now." preamble) into the listener-facing summary.

The upstream generation failures are out of scope here; this change hardens the feed renderer so neither broken-data case can ever produce ugly output again.

## What Changes

- `FeedGenerator` coalesces blank/whitespace topic labels to "Other", and omits the "Topics Covered" section entirely when no non-blank topic name exists (no more empty `<li>`).
- `FeedGenerator` cleans the script fallback used when both `showNotes` and `recap` are absent: it strips any leaked meta-commentary preamble and removes speaker tags (keeping the spoken text) so raw tags never reach the description.
- Both the plain `<description>` and the HTML `content:encoded` share the same cleaned script fallback.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `podcast-feed`: The "Feed content:encoded shows topic-representative sources" requirement gains rules for blank topic coalescing/omission and a sanitized script fallback for episodes lacking show notes and recap.

## Impact

- `src/main/kotlin/com/aisummarypodcast/podcast/FeedGenerator.kt` (`buildPlainDescription`, `buildHtmlDescription`, new `scriptFallback`)
- `src/test/kotlin/com/aisummarypodcast/podcast/FeedGeneratorTest.kt`
- No API, database, or configuration changes. Feeds regenerate with the hardened output on next publish.
