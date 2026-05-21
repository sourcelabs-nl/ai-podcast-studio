## Why

When the episode generation pipeline fails at any stage, the only recovery is to discard and re-run the entire pipeline from scratch. Because each stage involves non-deterministic LLM calls, re-running produces completely different scripts from the same articles. This wastes LLM credits and forces the user to accept a different episode than what was originally generated. The user needs to be able to retry from exactly the failed stage, preserving all work completed up to that point.

## What Changes

- Split `LlmPipeline.run()` into independently callable stage methods (`aggregateScoreAndFilter`, `dedup`, `compose`) so each stage can be invoked separately during retry
- Persist intermediate results eagerly during normal generation: save `episode_articles` links with topic data after dedup (before compose), save script text after compose (before finalization)
- Preserve `pipelineStage` on failure instead of clearing it to null, so the system knows which stage was running when the failure occurred
- Add a retry endpoint (`POST /episodes/{id}/retry`) that auto-detects the resume point from persisted state and continues from there
- Publish SSE events for each intermediate state save (dedup saved, script saved, generating recap, etc.) so the UI reflects real-time progress
- Add a "Retry" button on the frontend for FAILED episodes, showing which stage will be retried
- Add unit tests for all new retry and intermediate persistence logic

## Capabilities

### New Capabilities
- `pipeline-retry`: Ability to retry a failed episode from the exact stage that failed, preserving all previously completed LLM work. Includes resume point detection, intermediate state persistence, retry endpoint, and SSE event notifications.

### Modified Capabilities
- `podcast-pipeline`: Pipeline now persists intermediate results between stages (dedup results, script) and supports stage-level retry. The `pipelineStage` field is preserved on failure.
- `pipeline-progress`: New SSE events for intermediate state saves (`dedup_saved`, `script_saved`, `marking_processed`, `generating_recap`) and retry initiation (`episode.retrying`).
- `frontend-event-notifications`: New toast messages for intermediate pipeline stages and retry events.

## Impact

- **Backend**: `LlmPipeline.kt`, `PodcastService.kt`, `EpisodeService.kt`, `EpisodeController.kt`, `AudioGenerationService.kt` all modified
- **Frontend**: Episode detail page gets Retry button; event-context.tsx gets new toast messages
- **API**: New endpoint `POST /users/{userId}/podcasts/{podcastId}/episodes/{episodeId}/retry`
- **No schema changes**: All persistence uses existing database columns (`episode_articles.topic/topic_order`, `episodes.script_text`, `episodes.pipeline_stage`)
- **Backward compatible**: Existing approve/discard/regenerate flows unchanged. `LlmPipeline.run()` kept as convenience method delegating to new stage methods.
