## ADDED Requirements

### Requirement: Retry failed episode from detected resume point
The system SHALL provide a `POST /users/{userId}/podcasts/{podcastId}/episodes/{episodeId}/retry` endpoint that retries a FAILED episode from the appropriate stage. The endpoint SHALL return HTTP 202 with the detected resume point and run the remaining pipeline stages asynchronously. The endpoint SHALL reject non-FAILED episodes with HTTP 409.

#### Scenario: Retry episode that failed during compose (dedup results saved)
- **WHEN** a `POST .../episodes/{id}/retry` request is received for a FAILED episode that has `episode_articles` links but no `scriptText`
- **THEN** the system resets the episode to GENERATING, loads the saved articles from `episode_articles`, runs only the compose stage and finalization, and returns HTTP 202 with `resumePoint: "COMPOSE"`

#### Scenario: Retry episode that failed during TTS (script saved)
- **WHEN** a `POST .../episodes/{id}/retry` request is received for a FAILED episode that has non-empty `scriptText`
- **THEN** the system resets the episode to GENERATING, runs only finalization (TTS, recap, sources, mark processed), and returns HTTP 202 with `resumePoint: "POST_COMPOSE"`

#### Scenario: Retry episode that failed during early pipeline (no intermediate state)
- **WHEN** a `POST .../episodes/{id}/retry` request is received for a FAILED episode with no `episode_articles` links and empty `scriptText`
- **THEN** the system resets the episode to GENERATING, runs the full pipeline (aggregate, score, dedup, compose, finalize), and returns HTTP 202 with `resumePoint: "FULL_PIPELINE"`

#### Scenario: Retry non-FAILED episode rejected
- **WHEN** a `POST .../episodes/{id}/retry` request is received for an episode that is not in FAILED status
- **THEN** the system returns HTTP 409 with an error message

#### Scenario: Retry publishes SSE event
- **WHEN** a retry is initiated for a FAILED episode
- **THEN** an `episode.retrying` SSE event is published with the episode number and detected resume point

### Requirement: Resume point auto-detection from persisted state
The system SHALL determine the resume point for a FAILED episode by checking persisted state in this order: (1) if `scriptText` is non-empty, resume from POST_COMPOSE; (2) if `episode_articles` links exist, resume from COMPOSE; (3) otherwise, FULL_PIPELINE.

#### Scenario: Script present implies post-compose resume
- **WHEN** a FAILED episode has `scriptText = "Today in tech..."` and `episode_articles` links
- **THEN** the detected resume point is POST_COMPOSE

#### Scenario: Article links without script implies compose resume
- **WHEN** a FAILED episode has empty `scriptText` and 45 `episode_articles` links with topic assignments
- **THEN** the detected resume point is COMPOSE

#### Scenario: No intermediate state implies full pipeline
- **WHEN** a FAILED episode has empty `scriptText` and no `episode_articles` links
- **THEN** the detected resume point is FULL_PIPELINE

### Requirement: Episode reset for retry
The system SHALL reset a FAILED episode for retry by setting status to GENERATING, clearing `errorMessage`, and preserving all other fields (scriptText, episode_articles links, pipelineStage, token counts). The `hasActiveEpisode()` check SHALL prevent concurrent generation for the same podcast while the retry is in progress.

#### Scenario: Reset preserves intermediate state
- **WHEN** a FAILED episode with scriptText and episode_articles is reset for retry
- **THEN** the episode status becomes GENERATING, errorMessage is null, but scriptText and episode_articles links remain intact

#### Scenario: Concurrent generation blocked during retry
- **WHEN** an episode is reset to GENERATING for retry
- **THEN** `hasActiveEpisode()` returns true for that podcast, blocking scheduled and manual generation

### Requirement: Intermediate state persistence during pipeline
The system SHALL persist intermediate results eagerly between pipeline stages. After dedup completes, the system SHALL save `episode_articles` links with topic and topic_order, and update the episode with `filterModel` and dedup token counts. After compose completes, the system SHALL save `scriptText`, `composeModel`, and accumulated token counts to the episode.

#### Scenario: Dedup results persisted before compose
- **WHEN** the dedup stage completes successfully during episode generation
- **THEN** `episode_articles` links are saved with topic assignments, and the episode is updated with filterModel and dedup token counts, before the compose stage begins

#### Scenario: Script persisted before finalization
- **WHEN** the compose stage completes successfully during episode generation
- **THEN** the episode's scriptText and composeModel are saved, with llmInputTokens and llmOutputTokens accumulated from both dedup and compose stages, before finalization begins

#### Scenario: Compose failure preserves dedup results
- **WHEN** the compose stage throws an exception after dedup results were persisted
- **THEN** the episode is marked FAILED, but episode_articles links and dedup token counts remain in the database

### Requirement: Pipeline stage preserved on failure
The system SHALL preserve the `pipelineStage` value when marking an episode as FAILED, instead of clearing it to null. This applies to `failEpisode()`, `cleanupStaleGeneratingEpisodes()`, and async TTS failure handling.

#### Scenario: Failed episode retains pipeline stage
- **WHEN** an episode fails during the "composing" stage
- **THEN** the FAILED episode has `pipelineStage = "composing"` (not null)

#### Scenario: Stale episode on startup retains pipeline stage
- **WHEN** the application restarts and finds a GENERATING episode with `pipelineStage = "scoring"`
- **THEN** the episode is marked FAILED with `pipelineStage = "scoring"` preserved

### Requirement: Frontend retry button for failed episodes
The episode detail page SHALL display a "Retry" button for FAILED episodes. The button SHALL call `POST .../episodes/{id}/retry`. The page SHALL display the failed pipeline stage (e.g., "Failed at: composing") when `pipelineStage` is present on a FAILED episode.

#### Scenario: Retry button shown for failed episode
- **WHEN** the episode detail page loads for a FAILED episode
- **THEN** a "Retry" button is displayed alongside the existing Approve and Discard buttons

#### Scenario: Failed stage displayed
- **WHEN** the episode detail page loads for a FAILED episode with `pipelineStage = "composing"`
- **THEN** the error area shows "Failed at: composing" in addition to the error message

#### Scenario: Retry button triggers pipeline
- **WHEN** the user clicks the "Retry" button on a FAILED episode
- **THEN** the system calls `POST .../episodes/{id}/retry` and shows a toast with "Retrying episode from {resumePoint}..."
