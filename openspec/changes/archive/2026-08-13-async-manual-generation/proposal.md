## Why

The manual `POST /generate` and `/regenerate` endpoints ran the multi-minute generation pipeline inside the HTTP request as `suspend` handlers. Spring MVC adapts suspend handlers to a `Mono` with an async-request timeout; when the request times out (or the client disconnects), Spring cancels the coroutine. After the non-blocking-coroutines change made the pipeline fully suspending, that cancellation now propagates and aborts the in-flight generation (observed: `MonoCoroutine was cancelled`, episode left FAILED). Scheduler-driven generation is unaffected because it already runs in a background scope.

## What Changes

- Manual `/generate` and `/regenerate` run generation in a managed background `CoroutineScope` and return **202 Accepted** immediately with the new GENERATING episode id, instead of awaiting the pipeline in-request. Progress and completion continue to flow to the UI via SSE events.
- `/generate` returns **409 Conflict** when an episode is already generating for the podcast.
- `PodcastService` gains `generateBriefingAsync` and `regenerateEpisodeAsync`; the existing `generateBriefing` (used by the scheduler) is preserved by extracting the shared pipeline body.
- `EpisodeService.createGeneratingEpisode` gains an `updateLastGenerated` flag so regeneration does not bump `lastGeneratedAt` (which would make the scheduler skip the next cron run).
- `/regenerate-recap` is unchanged (a single short LLM call, not at timeout risk).

No frontend change: the UI already navigates on `episodeId` and tracks progress via SSE.

## Capabilities

### New Capabilities
- `async-manual-generation`: Manual episode generation and regeneration run as background jobs decoupled from the HTTP request lifecycle, returning 202 with the GENERATING episode id.

### Modified Capabilities
<!-- None: no existing spec's requirements change. -->

## Impact

- `PodcastController.generate` / `regenerate` (now non-suspend, return 202/409).
- `PodcastService` (`generateBriefingAsync`, `regenerateEpisodeAsync`, extracted `runGenerationPipeline`/`runRegeneration`, `pipelineScope`).
- `EpisodeService.createGeneratingEpisode` (`updateLastGenerated` flag).
- No API path or schema changes; response status changes from 200 to 202/409.
