## 1. Sources page enrichment (retrofit: already implemented)

- [x] 1.1 Extract a shared `scriptFallbackSummary` + `formatDuration` helper (`EpisodeSummaryText.kt`) and have `FeedGenerator` use it instead of its private copy
- [x] 1.2 Always render a Summary in `EpisodeSourcesGenerator` using `showNotes` → `recap` → sanitized script fallback
- [x] 1.3 Add a metadata line (duration, source count, listen link) under the date
- [x] 1.4 Add `EpisodeSourcesGeneratorTest` cases for the meta line, discussed-source count, and script-fallback summary
- [x] 1.5 Run `mvn test` and verify the full suite passes
