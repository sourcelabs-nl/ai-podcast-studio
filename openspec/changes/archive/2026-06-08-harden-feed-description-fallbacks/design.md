## Context

Episode descriptions in the RSS feed are rendered from stored episode data by `FeedGenerator`. Two real episodes exposed gaps: blank topic labels rendered as empty list items, and a missing show-notes/recap caused the raw tagged script to leak into the summary. This is a retrofit design: the change is already implemented in `FeedGenerator`.

## Goals / Non-Goals

**Goals:**
- The feed never renders an empty topic bullet, regardless of stored topic data.
- The feed never surfaces raw speaker tags or "about to write the script" preamble in the listener-facing description.

**Non-Goals:**
- Fixing the upstream generation failures that produced the blank topics / missing show notes.
- Backfilling the affected episodes' data (handled operationally, not in code).
- Changing the sources HTML page or the feed structure.

## Decisions

- **Blank topics coalesce to "Other"; section omitted when no real topic exists.** A label is "real" only when non-blank. If every label with a `topicOrder` is blank, the whole "Topics Covered" section is dropped (cleaner than a lone "Other" bullet for a fully-broken episode). When at least one real topic exists, remaining blanks render as "Other".
- **Shared, sanitized script fallback.** A single `scriptFallback` helper reuses the existing `stripLeadingMetaCommentary` (from `ComposerUtils`), strips speaker tags via `</?\w+>` while keeping inner spoken text, collapses whitespace, and truncates to 500 chars. Both `buildPlainDescription` and `buildHtmlDescription` use it so plain and HTML descriptions stay consistent.

## Risks / Trade-offs

- [Tag-stripping regex could remove a legitimate `<word>` in spoken text] → Scripts are tagged speaker turns, not markup-bearing prose; the risk is negligible and only affects the rare fallback path (episodes with neither show notes nor recap).
- [A fully blank-topic episode shows no topics] → Accepted; the "view all sources and show notes" link still covers navigation, and there are no real topic names to show.
