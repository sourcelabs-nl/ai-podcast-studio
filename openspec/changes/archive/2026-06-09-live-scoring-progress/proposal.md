## Why

The scoring stage is the longest part of episode generation: it scores and summarizes every unscored article concurrently, which can be hundreds of articles and run for many minutes. Until now the pipeline emitted a single `scoring` stage event with the total article count, so the frontend could only show a static "Scoring…" label. A long, opaque stage looks like the app is stuck. Surfacing live progress (scored X of N) tells the user generation is healthy and roughly how long is left.

## What Changes

- `ArticleScoreSummarizer.scoreSummarize` accepts an `onProgress(completed, total)` callback, invoked as each article finishes (success or give-up). Callbacks are throttled to at most ~50 per run so large batches do not flood the event bus.
- `LlmPipeline` (both the generation path and the preview path) passes a callback that re-emits the `scoring` stage event enriched with a `scoredCount` field alongside the existing `articleCount`.
- The frontend podcast episodes page consumes incremental `scoring` events into local state (keyed by episode id) and renders a live `Scoring X / N` counter plus a progress bar on the GENERATING episode row. Progress ticks update local state only and do not trigger an episode-list re-fetch.

## Capabilities

### Modified Capabilities
- `podcast-pipeline`: the scoring stage emits incremental progress (`scoredCount` / `articleCount`) as articles complete, not just a one-time total.

## Impact

- **Modified backend**: `ArticleScoreSummarizer.kt` (progress callback + throttle), `LlmPipeline.kt` (callback wiring on generation and preview paths), `LlmPipelineTest.kt` (mock signature).
- **Modified frontend**: `frontend/src/app/podcasts/[podcastId]/page.tsx` (live counter + progress bar, scoring-tick handling).
- No schema, persistence, or dependency changes. Progress is transient (SSE + local React state only); nothing is written to the database per article.
