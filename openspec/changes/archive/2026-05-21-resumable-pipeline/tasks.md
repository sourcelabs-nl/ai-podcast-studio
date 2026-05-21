## 1. Split LlmPipeline into stage methods

- [x] 1.1 Add `DedupStageResult` and `ComposeStageResult` data classes to `LlmPipeline.kt`
- [x] 1.2 Extract `aggregateScoreAndFilter()` method from `run()` (stages 1-2 + eligibility + cost gate)
- [x] 1.3 Extract `dedup()` method from `run()` (stage 3: dedup filter + follow-up annotations + topic labels)
- [x] 1.4 Extract `compose()` method from `run()` (stage 4: style-based composer selection)
- [x] 1.5 Refactor `run()` to delegate to the three stage methods, returning existing `PipelineResult`
- [x] 1.6 Update `LlmPipelineTest.kt` with tests for split stage methods and backward compatibility

## 2. Preserve pipeline stage on failure

- [x] 2.1 Update `EpisodeService.failEpisode()` to preserve `pipelineStage` instead of clearing to null
- [x] 2.2 Update `EpisodeService.cleanupStaleGeneratingEpisodes()` to preserve `pipelineStage`
- [x] 2.3 Update `AudioGenerationService.doGenerateAudio()` to preserve `pipelineStage` on failure
- [x] 2.4 Add test in `EpisodeServiceTest.kt`: `failEpisode preserves pipelineStage`

## 3. Add intermediate save methods to EpisodeService

- [x] 3.1 Add `saveDedupResults(episode, dedupStageResult)` method with `@Transactional` (saves episode_articles links + updates episode with filterModel/tokens)
- [x] 3.2 Add `saveComposeResult(episode, composeStageResult)` method with `@Transactional` (saves scriptText + accumulated tokens)
- [x] 3.3 Add `finalizeEpisode(episode, podcast)` method extracted from `createEpisodeFromPipelineResult()` (set status, mark processed, recap, sources, lastGeneratedAt) with idempotent sub-steps
- [x] 3.4 Add `resetForRetry(episode)` method (set GENERATING, clear errorMessage, preserve rest)
- [x] 3.5 Refactor `createEpisodeFromPipelineResult()` to delegate to new methods for backward compatibility
- [x] 3.6 Add tests in `EpisodeServiceTest.kt` for `saveDedupResults`, `saveComposeResult`, `resetForRetry`, `finalizeEpisode`

## 4. Refactor PodcastService.generateBriefing() for intermediate persistence

- [x] 4.1 Refactor `generateBriefing()` to call stage methods with intermediate saves: aggregate+score → dedup → saveDedupResults → compose → saveComposeResult → finalizeEpisode
- [x] 4.2 Add SSE events after each intermediate save (`dedup_saved`, `script_saved`)
- [x] 4.3 Add SSE events during finalization (`marking_processed`, `generating_recap`)
- [x] 4.4 Verify `mvn test` passes after refactor

## 5. Add retry endpoint and logic

- [x] 5.1 Add resume point detection logic to `PodcastService` (check scriptText → episode_articles → full pipeline)
- [x] 5.2 Add `retryEpisode(episode, podcast)` async method to `PodcastService` with resume logic for FULL_PIPELINE, COMPOSE, and POST_COMPOSE
- [x] 5.3 Add `POST /{episodeId}/retry` endpoint to `EpisodeController` returning 202 with resume point
- [x] 5.4 Publish `episode.retrying` SSE event on retry initiation
- [x] 5.5 Add tests in `PodcastServiceTest.kt` for all three resume points and SSE event

## 6. Frontend changes

- [x] 6.1 Add toast messages in `event-context.tsx` for new stages (`dedup_saved`, `script_saved`, `deduplicating`, `marking_processed`, `generating_recap`) and `episode.retrying` event
- [x] 6.2 Add "Retry" button on episode detail page for FAILED episodes calling `POST .../retry`
- [x] 6.3 Display failed pipeline stage on episode detail page (e.g., "Failed at: composing")

## 7. Verification

- [x] 7.1 Run `mvn test` to verify all tests pass
- [x] 7.2 Manual test: generate episode, verify intermediate saves occur (episode_articles exist before compose)
- [x] 7.3 Manual test: verify existing approve/discard/regenerate flows still work
