## MODIFIED Requirements

### Requirement: Failed episode creation on generation errors
When the briefing generation pipeline throws an exception (e.g., invalid model configuration, LLM API errors), the system SHALL create an episode with status `FAILED` and store the error message in the `errorMessage` field. The pipeline stage value SHALL be preserved (not cleared to null) so the UI can display which stage failed. The podcast's `lastGeneratedAt` SHALL already have been set when the GENERATING episode was created at pipeline start. An `episode.failed` event SHALL be published so connected clients are notified. `PodcastService.generateBriefing()` SHALL return a `GenerateBriefingResult` containing the episode (or null), a `failed` flag, and an optional error message.

#### Scenario: Pipeline error creates failed episode (scheduler)
- **WHEN** the scheduler triggers briefing generation for a podcast and the LLM pipeline throws an exception (e.g., unknown model name)
- **THEN** the GENERATING episode is transitioned to FAILED with the error message and the current `pipelineStage` preserved (`lastGeneratedAt` was already set at pipeline start), an `episode.failed` event is published, and the scheduler logs the failure without retrying

#### Scenario: Pipeline error creates failed episode (manual trigger)
- **WHEN** a `POST /users/{userId}/podcasts/{podcastId}/generate` request is received and the LLM pipeline throws an exception
- **THEN** a FAILED episode is created with the error message and `pipelineStage` preserved, and the endpoint returns HTTP 500 with the error message and the failed episode ID

#### Scenario: Failed episode is visible in UI
- **WHEN** a FAILED episode is created due to a pipeline error
- **THEN** the episode appears in the episode list with status `FAILED`, the error message is visible on the episode detail page, and the failed pipeline stage is shown

### Requirement: Early episode creation with GENERATING status
The pipeline SHALL create an episode row with status `GENERATING` and empty script text at the start of generation, before any LLM calls. The podcast's `lastGeneratedAt` SHALL be set at this point (when the GENERATING episode is created), not on completion. The episode's `pipelineStage` field SHALL be updated as the pipeline progresses through stages (`aggregating`, `scoring`, `deduplicating`, `composing`, `tts`). Intermediate results SHALL be persisted eagerly: `episode_articles` links with topic data after dedup completes, and `scriptText` after compose completes. On successful completion, the episode SHALL be updated with the final script, costs, token usage, and transitioned to `PENDING_REVIEW` or `GENERATED`. On failure, the episode SHALL be transitioned to `FAILED` with an error message and the `pipelineStage` preserved.

#### Scenario: Episode created at pipeline start
- **WHEN** a podcast generation is triggered
- **THEN** an episode row is created with status `GENERATING`, empty `scriptText`, and `pipelineStage` set to the first active stage

#### Scenario: Pipeline stage updates
- **WHEN** the pipeline transitions between stages (aggregating, scoring, deduplicating, composing)
- **THEN** the episode's `pipelineStage` field is updated in the database

#### Scenario: Dedup results persisted before compose
- **WHEN** the dedup stage completes during generation
- **THEN** `episode_articles` links are saved with topic and topic_order, and the episode is updated with filterModel and dedup token counts

#### Scenario: Script persisted before finalization
- **WHEN** the compose stage completes during generation
- **THEN** the episode's scriptText, composeModel, and accumulated token counts are saved

#### Scenario: Successful completion
- **WHEN** the pipeline completes successfully
- **THEN** the episode is updated with script, costs, and status `PENDING_REVIEW` (if requireReview) or proceeds to TTS, and `pipelineStage` is set to null

#### Scenario: Pipeline failure
- **WHEN** the pipeline fails with an error
- **THEN** the existing GENERATING episode is transitioned to `FAILED` with the error message and `pipelineStage` preserved

### Requirement: Startup cleanup of stale GENERATING episodes
On application startup, the system SHALL find all episodes with status `GENERATING` or `GENERATING_AUDIO` and transition them to `FAILED` with error message "Generation interrupted by application restart". The `pipelineStage` value SHALL be preserved (not cleared to null).

#### Scenario: Stale episodes on startup
- **WHEN** the application starts and there are episodes with status `GENERATING` or `GENERATING_AUDIO`
- **THEN** they are all updated to status `FAILED` with an appropriate error message and their `pipelineStage` preserved
