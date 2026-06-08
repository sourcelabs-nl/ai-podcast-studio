## Why

The per-episode sources HTML page (linked from every RSS episode description) was sparse: it showed the podcast name, date, and source links, but the Summary only appeared when a `recap` existed (so episodes like 2026-06-05, which had no recap, showed no summary at all), and there was no episode metadata (length, source count) or way to play the episode from the page.

## What Changes

- The sources page always renders a **Summary**, using `showNotes` → `recap` → a sanitized script-opening fallback (same helper the RSS feed uses), so every episode has one even when show notes/recap are missing.
- A **metadata line** under the date shows the episode **duration** (when known), the **number of sources** (discussed articles when topic data is present, otherwise all linked articles), and a **Listen** link to the episode audio.
- The script fallback strips leaked preamble and speaker tags, consistent with the RSS description.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `episode-sources-file`: The generated HTML gains an always-present Summary (with fallback) and a metadata line (duration, source count, listen link).

## Impact

- `src/main/kotlin/com/aisummarypodcast/podcast/EpisodeSourcesGenerator.kt`
- `src/main/kotlin/com/aisummarypodcast/podcast/EpisodeSummaryText.kt` (shared `scriptFallbackSummary` + `formatDuration`, also used by `FeedGenerator`)
- `src/test/kotlin/com/aisummarypodcast/podcast/EpisodeSourcesGeneratorTest.kt`
- No API, database, or configuration changes. Pages regenerate on the next episode publish/recap regeneration.
