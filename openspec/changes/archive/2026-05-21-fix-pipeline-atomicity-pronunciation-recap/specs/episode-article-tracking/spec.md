## MODIFIED Requirements

### Requirement: Shared episode creation logic in EpisodeService
The system SHALL provide a method in `EpisodeService` that encapsulates the post-pipeline episode creation logic: saving the episode, saving episode-article links, marking articles as processed, generating a recap, and updating `lastGeneratedAt` on the podcast. Both `BriefingGenerationScheduler` and `PodcastController.generate()` SHALL delegate to this shared method instead of reimplementing the logic independently.

Articles SHALL only be marked as `is_processed = true` AFTER being successfully linked to the episode via `episode_articles`. The `LlmPipeline` SHALL NOT mark articles as processed; this responsibility belongs exclusively to `EpisodeService.createEpisodeFromPipelineResult()`.

The system SHALL NOT advance `lastGeneratedAt` when the pipeline returns no results (null). Only the successful creation of an episode SHALL update this timestamp. This ensures that articles published before the timestamp remain visible in the upcoming view even when a pipeline run finds no relevant content to compose.

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
