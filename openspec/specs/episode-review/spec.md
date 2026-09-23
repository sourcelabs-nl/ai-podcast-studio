# Capability: Episode Review

## Purpose

Review workflow for episode scripts — status lifecycle, script editing, approval/discard, and async TTS trigger after approval.
## Requirements
### Requirement: Episode status lifecycle
Each episode SHALL have a `status` field with one of the following values: `PENDING_REVIEW`, `APPROVED`, `GENERATING_AUDIO`, `GENERATED`, `FAILED`, `DISCARDED`. The status determines where the episode is in the review-to-audio pipeline. `GENERATING_AUDIO` indicates that TTS audio generation is actively in progress. An episode can enter `FAILED` status either from a TTS generation failure (after approval) or from a pipeline generation error (e.g., invalid model configuration). In the pipeline error case, a FAILED episode is created with an empty `scriptText`, the error stored in `errorMessage`, and `lastGeneratedAt` updated to prevent scheduler retries. After each status transition, the service SHALL publish a `PodcastEvent` via `ApplicationEventPublisher` to notify connected clients.

A focus episode (one with a non-null `focus`) SHALL always be created with status `PENDING_REVIEW`, regardless of the podcast's `requireReview` setting.

#### Scenario: New episode created with review enabled
- **WHEN** the pipeline generates a script for a podcast with `requireReview = true`
- **THEN** an episode is created with status `PENDING_REVIEW`, the `scriptText` populated, `audioFilePath` and `durationSeconds` set to null, and an `episode.created` event is published

#### Scenario: New episode created without review
- **WHEN** the pipeline generates a script for a regular (non-focus) episode of a podcast with `requireReview = false`
- **THEN** the episode is created with status `GENERATED` after TTS completes, with all fields populated, and an `episode.generated` event is published

#### Scenario: Focus episode always requires review
- **WHEN** the pipeline generates a focus episode for a podcast with `requireReview = false`
- **THEN** the episode is created with status `PENDING_REVIEW`, exactly as it would be if `requireReview` were `true`

### Requirement: List episodes for a podcast
The system SHALL provide an endpoint to list episodes for a podcast, with optional status filtering.

#### Scenario: List all episodes
- **WHEN** a `GET /users/{userId}/podcasts/{podcastId}/episodes` request is received
- **THEN** the system returns HTTP 200 with a JSON array of all episodes for that podcast, ordered by `generatedAt` descending

#### Scenario: List episodes filtered by status
- **WHEN** a `GET /users/{userId}/podcasts/{podcastId}/episodes?status=PENDING_REVIEW` request is received
- **THEN** the system returns HTTP 200 with only episodes matching the given status

#### Scenario: List episodes for non-existing podcast
- **WHEN** a `GET /users/{userId}/podcasts/{podcastId}/episodes` request is received for a podcast that does not exist or belongs to a different user
- **THEN** the system returns HTTP 404

### Requirement: Get single episode
The system SHALL provide an endpoint to retrieve a single episode by ID, including its script text and show notes.

#### Scenario: Get existing episode
- **WHEN** a `GET /users/{userId}/podcasts/{podcastId}/episodes/{episodeId}` request is received for an existing episode belonging to the podcast
- **THEN** the system returns HTTP 200 with the episode details including `id`, `status`, `scriptText`, `showNotes`, `audioFilePath`, `durationSeconds`, and `generatedAt`

#### Scenario: Get non-existing episode
- **WHEN** a `GET /users/{userId}/podcasts/{podcastId}/episodes/{episodeId}` request is received for an episode that does not exist or belongs to a different podcast
- **THEN** the system returns HTTP 404

### Requirement: Edit episode script
The system SHALL allow editing the script text of an episode that is in `PENDING_REVIEW` status.

#### Scenario: Edit script of pending episode
- **WHEN** a `PUT /users/{userId}/podcasts/{podcastId}/episodes/{episodeId}/script` request is received with a JSON body containing `scriptText`, and the episode status is `PENDING_REVIEW`
- **THEN** the system updates the episode's `scriptText` and returns HTTP 200 with the updated episode

#### Scenario: Edit script of non-pending episode
- **WHEN** a `PUT /users/{userId}/podcasts/{podcastId}/episodes/{episodeId}/script` request is received and the episode status is not `PENDING_REVIEW`
- **THEN** the system returns HTTP 409 (Conflict) with an error message indicating the episode is not in a reviewable state

### Requirement: Approve episode script
The system SHALL allow approving an episode script, which triggers async TTS generation. Each status transition during the approval and generation flow SHALL publish a corresponding event.

#### Scenario: Approve pending episode
- **WHEN** a `POST /users/{userId}/podcasts/{podcastId}/episodes/{episodeId}/approve` request is received and the episode status is `PENDING_REVIEW`
- **THEN** the system updates the episode status to `APPROVED`, publishes an `episode.approved` event, triggers TTS generation asynchronously, and returns HTTP 202 (Accepted)

#### Scenario: Approve non-pending episode
- **WHEN** a `POST /users/{userId}/podcasts/{podcastId}/episodes/{episodeId}/approve` request is received and the episode status is not `PENDING_REVIEW`
- **THEN** the system returns HTTP 409 (Conflict) with an error message

#### Scenario: TTS generation starts
- **WHEN** the async TTS pipeline begins generating audio for an approved episode
- **THEN** the episode status is updated to `GENERATING_AUDIO` in the database, and an `episode.audio.started` event is published

#### Scenario: TTS generation succeeds after approval
- **WHEN** the async TTS pipeline completes successfully for an episode in `GENERATING_AUDIO` status
- **THEN** the episode status is updated to `GENERATED`, `audioFilePath` and `durationSeconds` are populated, and an `episode.generated` event is published

#### Scenario: TTS generation fails after approval
- **WHEN** the async TTS pipeline fails for an episode in `GENERATING_AUDIO` status
- **THEN** the episode status is updated to `FAILED` and an `episode.failed` event is published

### Requirement: Retry failed episode
The system SHALL allow re-triggering TTS for a `FAILED` episode by approving it again.

#### Scenario: Approve failed episode
- **WHEN** a `POST /users/{userId}/podcasts/{podcastId}/episodes/{episodeId}/approve` request is received and the episode status is `FAILED`
- **THEN** the system updates the episode status to `APPROVED`, publishes an `episode.approved` event, triggers TTS generation asynchronously, and returns HTTP 202

### Requirement: Discard episode script
The system SHALL allow discarding an episode that is in `PENDING_REVIEW`, `GENERATED` or `FAILED` status. Episodes in `GENERATING_AUDIO` status SHALL NOT be discardable. Episodes with any publication in `PUBLISHED` status SHALL NOT be discardable, whatever their status: the system SHALL return HTTP 409 with the message "Episode is published to <targets>; unpublish it first", naming each published target, and SHALL leave the episode and its articles unchanged. The check SHALL live in the service layer, so every discard path enforces it. When an episode is discarded, the service SHALL publish an `episode.discarded` event. The system SHALL handle linked articles differently based on whether they are aggregated:

- **Non-aggregated articles** (0 or 1 linked posts in `post_articles`): The system SHALL reset the article's `is_processed` flag to `false`, preserving the article's score and summary for reuse.
- **Aggregated articles** (2+ linked posts in `post_articles`): The system SHALL delete all `post_articles` entries for the article, then delete the article itself. This makes the original posts unlinked and eligible for re-aggregation on the next pipeline run.

The system SHALL look up linked articles via the `episode_articles` table. If no episode-article links exist (for episodes created before the tracking feature was added), the system SHALL log a warning indicating that no articles could be reset. A `FAILED` episode is discarded without resetting its articles.

#### Scenario: Discard pending episode with non-aggregated articles
- **WHEN** a `POST /users/{userId}/podcasts/{podcastId}/episodes/{episodeId}/discard` request is received, the episode status is `PENDING_REVIEW`, and the episode has 3 linked articles that each have 0 or 1 `post_articles` entries
- **THEN** the system updates the episode status to `DISCARDED`, resets all 3 articles to `is_processed = false`, and returns HTTP 200

#### Scenario: Discard pending episode with aggregated articles
- **WHEN** a `POST /users/{userId}/podcasts/{podcastId}/episodes/{episodeId}/discard` request is received, the episode status is `PENDING_REVIEW`, and the episode has 1 linked article that has 5 `post_articles` entries
- **THEN** the system updates the episode status to `DISCARDED`, deletes all 5 `post_articles` entries for that article, deletes the article, and returns HTTP 200

#### Scenario: Discard pending episode with mixed article types
- **WHEN** a `POST /users/{userId}/podcasts/{podcastId}/episodes/{episodeId}/discard` request is received, the episode status is `PENDING_REVIEW`, and the episode has 2 non-aggregated articles and 1 aggregated article (with 4 `post_articles` entries)
- **THEN** the system updates the episode status to `DISCARDED`, resets the 2 non-aggregated articles to `is_processed = false`, deletes the 4 `post_articles` entries and the aggregated article, and returns HTTP 200

#### Scenario: Discard pending episode without article links
- **WHEN** a `POST /users/{userId}/podcasts/{podcastId}/episodes/{episodeId}/discard` request is received, the episode status is `PENDING_REVIEW`, and the episode has no linked articles in `episode_articles`
- **THEN** the system updates the episode status to `DISCARDED`, logs a warning that no articles could be reset, and returns HTTP 200

#### Scenario: Discard generated episode
- **WHEN** a `POST /users/{userId}/podcasts/{podcastId}/episodes/{episodeId}/discard` request is received and the episode status is `GENERATED` and the episode has no PUBLISHED publications
- **THEN** the system updates the episode status to `DISCARDED`, handles linked articles as above, and returns HTTP 200

#### Scenario: Discard non-discardable episode
- **WHEN** a `POST /users/{userId}/podcasts/{podcastId}/episodes/{episodeId}/discard` request is received and the episode status is not `PENDING_REVIEW`, `GENERATED` or `FAILED`
- **THEN** the system returns HTTP 409 (Conflict) with an error message

#### Scenario: Discard GENERATING_AUDIO episode blocked
- **WHEN** a `POST /users/{userId}/podcasts/{podcastId}/episodes/{episodeId}/discard` request is received and the episode status is `GENERATING_AUDIO`
- **THEN** the system returns HTTP 409 (Conflict) with an error message indicating audio generation is in progress

#### Scenario: Discard published episode blocked
- **WHEN** a `POST /users/{userId}/podcasts/{podcastId}/episodes/{episodeId}/discard` request is received and the episode has a PUBLISHED publication to `ftp`
- **THEN** the system returns HTTP 409 (Conflict) with the error "Episode is published to ftp; unpublish it first", and the episode keeps its status and its publication

#### Scenario: Unpublished or failed publications do not block discard
- **WHEN** a discard request is received for an episode whose publications are all `UNPUBLISHED` or `FAILED`
- **THEN** the episode is discarded as usual

### Requirement: Focus episode review shows selection and research context
The review screen for a focus episode SHALL show: the articles selected for it, the research sources (query and results) recorded for it, and an estimated spoken length. The estimated length SHALL be computed from the script's word count using the same words-per-minute figure already used elsewhere in the product for this estimate.

#### Scenario: Review screen shows selected articles and sources
- **WHEN** a focus episode in `PENDING_REVIEW` is opened for review
- **THEN** the screen lists the articles linked to the episode and the research sources recorded for it

#### Scenario: Review screen shows an estimated length
- **WHEN** a focus episode's script contains 1500 words
- **THEN** the review screen shows an estimated spoken length derived from that word count using the product's existing words-per-minute figure

### Requirement: Feedback-driven recompose of a focus episode
A focus episode in `PENDING_REVIEW` SHALL support a feedback-driven recompose action: given a feedback text, the system recomposes the script (rerunning research) using the episode's already-selected articles, without re-running article selection. The action SHALL be repeatable. The feedback text SHALL be stored on the episode as `review_feedback`, holding the most recently submitted feedback. The episode SHALL remain in `PENDING_REVIEW` after a feedback-driven recompose. When the feedback text asks for a different script length, it overrides the podcast's configured target word count for that recompose.

#### Scenario: Recompose with feedback keeps the same articles
- **WHEN** a focus episode in `PENDING_REVIEW` receives a "make it more technical" feedback recompose request
- **THEN** the script is regenerated using the same set of linked articles, `review_feedback` is updated to "make it more technical", and the episode stays in `PENDING_REVIEW`

#### Scenario: Feedback recompose is repeatable
- **WHEN** a focus episode has already been recomposed once with feedback and receives a second, different feedback recompose request
- **THEN** the script is regenerated again against the same articles, and `review_feedback` is updated to the latest feedback text

#### Scenario: Feedback overrides the target word count
- **WHEN** a focus episode's feedback recompose asks for "twice as long"
- **THEN** the recomposed script targets that requested length instead of the podcast's configured target word count

#### Scenario: Approving after feedback recompose starts TTS as usual
- **WHEN** a focus episode that has gone through one or more feedback recomposes is approved
- **THEN** TTS generation starts through the existing approval flow, exactly as for any other `PENDING_REVIEW` episode

### Requirement: Shared episode creation logic in EpisodeService
The system SHALL provide a method in `EpisodeService` that encapsulates the post-pipeline episode creation logic: saving the episode, saving episode-article links, marking articles as processed, generating a recap, and updating `lastGeneratedAt` on the podcast. Both `BriefingGenerationScheduler` and `PodcastController.generate()` SHALL delegate to this shared method instead of reimplementing the logic independently.

Articles SHALL only be marked as `is_processed = true` AFTER being successfully linked to the episode via `episode_articles`. The `LlmPipeline` SHALL NOT mark articles as processed; this responsibility belongs exclusively to `EpisodeService.createEpisodeFromPipelineResult()`.

The system SHALL NOT advance `lastGeneratedAt` when the pipeline returns no results (null). Only the successful creation of an episode SHALL update this timestamp. This ensures that articles published before the timestamp remain visible in the upcoming view even when a pipeline run finds no relevant content to compose.

`createEpisodeFromPipelineResult` SHALL accept an optional `overrideGeneratedAt` parameter. When provided, the episode's `generatedAt` field SHALL use this value instead of the current time.

`createEpisodeFromPipelineResult` SHALL accept an optional `updateLastGenerated` parameter (default: `true`). When `false`, the method SHALL skip updating the podcast's `lastGeneratedAt` timestamp.

Existing callers that do not pass these parameters SHALL behave identically to before (defaults preserve current behavior).

#### Scenario: Scheduler delegates to shared creation logic
- **WHEN** `BriefingGenerationScheduler` completes a pipeline run with a non-null result
- **THEN** it calls the shared `EpisodeService` method which saves the episode, links articles, marks articles as processed, generates recap, and updates `lastGeneratedAt`

#### Scenario: Manual generate delegates to shared creation logic
- **WHEN** `PodcastController.generate()` completes a pipeline run with a non-null result
- **THEN** it calls the same shared `EpisodeService` method which saves the episode, links articles, marks articles as processed, generates recap, and updates `lastGeneratedAt`

#### Scenario: Episode recap generated for manually triggered episodes
- **WHEN** a manual generation produces an episode
- **THEN** a recap is generated and stored on the episode, matching the behavior of scheduled generation

#### Scenario: Pipeline returns no results
- **WHEN** `llmPipeline.run()` returns null (no relevant articles to compose)
- **THEN** the system SHALL NOT update `lastGeneratedAt` on the podcast

#### Scenario: Pipeline returns results and episode is created
- **WHEN** `llmPipeline.run()` returns a `PipelineResult` and an episode is successfully created
- **THEN** `lastGeneratedAt` SHALL be updated to the current timestamp

#### Scenario: Articles marked processed only after linking
- **WHEN** the pipeline composes a briefing from 5 articles and an episode is created
- **THEN** all 5 articles are linked to the episode via `episode_articles` first, and only then marked as `is_processed = true`

#### Scenario: Pipeline does not mark articles as processed
- **WHEN** `LlmPipeline.run()` completes composition
- **THEN** the pipeline SHALL NOT set `is_processed = true` on any article; it returns the article IDs in `PipelineResult` for the caller to handle

