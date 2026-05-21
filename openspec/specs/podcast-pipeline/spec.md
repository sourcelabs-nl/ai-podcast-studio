# Capability: Podcast Pipeline

## Purpose

Per-podcast briefing generation pipeline, including podcast-scoped scheduling, LLM processing with per-podcast settings, TTS with per-podcast voice/speed, and manual generation trigger.
## Requirements
### Requirement: Briefing generation uses per-podcast schedule
The `BriefingGenerationScheduler` SHALL launch a coroutine loop on `ApplicationReadyEvent` that runs every 60 seconds, using its own `CoroutineScope(Dispatchers.IO + SupervisorJob())` so it runs independently from other schedulers (e.g., source polling) and cannot be starved by them. The scope SHALL be cancelled on `@PreDestroy` for graceful shutdown. Each iteration SHALL query all podcasts, evaluate each podcast's `cron` expression against the current time in the podcast's configured timezone (using `ZoneId.of(podcast.timezone)`) and `last_generated_at`, and trigger the pipeline for podcasts that are due. The scheduler SHALL skip generation for any podcast that has an active episode (status `GENERATING`, `PENDING_REVIEW`, `APPROVED`, or `GENERATING_AUDIO`). The scheduler SHALL skip any cron trigger whose scheduled time is more than 30 minutes before the current time, logging a WARN-level message for each skipped trigger. The scheduler SHALL advance through all stale triggers to find the next actionable one without modifying `last_generated_at`.

#### Scenario: Scheduled briefing respects individual cron expressions
- **WHEN** the scheduler runs and podcast A (cron: daily 06:00, timezone: Europe/Amsterdam, last generated yesterday) and podcast B (cron: daily 18:00, timezone: UTC, last generated today at 18:00) exist, and it is currently 06:05 CEST
- **THEN** the system triggers the pipeline for podcast A only

#### Scenario: Scheduled briefing with no podcasts
- **WHEN** the scheduler runs and no podcasts exist
- **THEN** the system completes without generating any episodes

#### Scenario: Newly created podcast runs on first check
- **WHEN** the scheduler runs and a podcast has `last_generated_at` set to null and its cron expression indicates it should have run already today in the podcast's timezone
- **THEN** the system triggers the pipeline for that podcast

#### Scenario: Scheduled briefing skips when active episode exists
- **WHEN** the scheduler runs and a podcast is due for generation, but an episode with status `GENERATING`, `PENDING_REVIEW`, `APPROVED`, or `GENERATING_AUDIO` already exists for that podcast
- **THEN** the system skips generation for that podcast (regardless of the podcast's `requireReview` setting)

#### Scenario: Scheduled briefing generates when no active episode
- **WHEN** the scheduler runs and a podcast is due for generation, and no episode with status `GENERATING`, `PENDING_REVIEW`, `APPROVED`, or `GENERATING_AUDIO` exists for that podcast
- **THEN** the system triggers the pipeline for that podcast

#### Scenario: Missed trigger is skipped when system was down
- **WHEN** the scheduler runs and a podcast (cron: daily 15:00, timezone: Europe/Amsterdam, last generated yesterday at 15:00 CEST) exists, and the current time is today at 18:00 CEST (3 hours past the trigger)
- **THEN** the system skips the missed trigger, logs a WARN message, and does not generate an episode

#### Scenario: Trigger within staleness window is executed
- **WHEN** the scheduler runs and a podcast (cron: daily 15:00, timezone: Europe/Amsterdam, last generated yesterday at 15:00 CEST) exists, and the current time is today at 15:10 CEST (10 minutes past the trigger)
- **THEN** the system triggers the pipeline for that podcast

#### Scenario: Multiple missed triggers are skipped
- **WHEN** the scheduler runs and a podcast (cron: daily 15:00, timezone: Europe/Amsterdam, last generated 3 days ago) exists, and the current time is today at 10:00 CEST
- **THEN** the system skips all 3 missed triggers (each logged at WARN level) and waits for today's 15:00 trigger

#### Scenario: Skipped triggers do not update last_generated_at
- **WHEN** the scheduler skips a stale trigger for a podcast
- **THEN** the podcast's `last_generated_at` remains unchanged

#### Scenario: DST transition handled correctly
- **WHEN** a podcast has cron `0 0 6 * * *` with timezone `Europe/Amsterdam`, and DST transitions from CET to CEST (clocks spring forward)
- **THEN** the scheduler triggers at 06:00 CEST (which is 04:00 UTC), not at 06:00 CET (05:00 UTC)

### Requirement: LLM pipeline scoped to podcast's sources, topic, and model
The `LlmPipeline` SHALL accept a podcast (including its customization settings). It SHALL filter and summarize only articles belonging to that podcast's sources, using the podcast's `topic` for relevance filtering and the podcast's `llm_model` (or global default) for LLM calls. It SHALL use a `ChatClient` created with the owning user's API key (or global fallback).

#### Scenario: Filter articles by podcast's sources
- **WHEN** `LlmPipeline.run(podcast)` is called
- **THEN** it queries unfiltered articles only from sources where `source.podcast_id = podcastId`, and queries relevant unprocessed articles only from those same sources

#### Scenario: Use podcast's topic for relevance filtering
- **WHEN** the relevance filter runs for a podcast with topic "Kotlin and JVM development"
- **THEN** it uses "Kotlin and JVM development" as the topic in the relevance prompt

#### Scenario: Use podcast's LLM model
- **WHEN** a podcast has `llm_model` set to `"openai/gpt-4o-mini"`
- **THEN** the LLM pipeline uses this model for relevance filtering, summarization, and script composition

#### Scenario: Use user's API key for LLM calls
- **WHEN** a podcast's owning user has an "openrouter" API key stored
- **THEN** the LLM pipeline creates a ChatClient using that API key

#### Scenario: No relevant articles for podcast
- **WHEN** `LlmPipeline.run(podcast)` is called and no relevant unprocessed articles exist for the podcast's sources
- **THEN** it returns null and no episode is generated for that podcast

### Requirement: Briefing composer uses podcast style and custom instructions
The `BriefingComposer` SHALL adapt its system prompt based on the podcast's `style` field and append any `custom_instructions`. It SHALL target the podcast's `target_words` (or global default).

#### Scenario: Compose with casual style
- **WHEN** the briefing composer runs for a podcast with style `"casual"` and target_words `800`
- **THEN** it uses a conversational tone in the prompt and targets approximately 800 words

#### Scenario: Compose with custom instructions
- **WHEN** the briefing composer runs for a podcast with custom_instructions `"Use Dutch language"`
- **THEN** the custom instructions are appended to the system prompt

#### Scenario: Compose with defaults
- **WHEN** the briefing composer runs for a podcast with no custom style, target_words, or custom_instructions
- **THEN** it uses the "news-briefing" style prompt and the global target word count

### Requirement: TTS pipeline uses podcast voice and speed settings
The `TtsPipeline` SHALL use the podcast's `tts_voice` and `tts_speed` settings when generating audio. It SHALL use a TTS model created with the owning user's OpenAI API key (or global fallback). Generated episodes SHALL be stored with a reference to the podcast.

#### Scenario: Generate episode with custom voice and speed
- **WHEN** `TtsPipeline.generate(script, podcast)` is called for a podcast with `tts_voice: "alloy"` and `tts_speed: 1.25`
- **THEN** the TTS service generates audio using the "alloy" voice at 1.25x speed

#### Scenario: Generate episode with user's TTS API key
- **WHEN** a podcast's owning user has an "openai" API key stored
- **THEN** the TTS pipeline uses that API key for TTS calls

#### Scenario: Audio stored in podcast-scoped directory
- **WHEN** `TtsPipeline.generate(script, podcast)` is called
- **THEN** the audio file is saved at `data/episodes/{podcastId}/briefing-{timestamp}.mp3` and the episode record is saved with the podcast's ID

#### Scenario: Podcast episode directory creation
- **WHEN** an episode is generated for a podcast that has no previous episodes
- **THEN** the system creates the `data/episodes/{podcastId}/` directory before saving the audio file

### Requirement: Manual briefing generation per podcast
The system SHALL allow triggering briefing generation for a specific podcast via REST API, bypassing the cron schedule. When the podcast has `requireReview = true`, the manual trigger SHALL create an episode with status `PENDING_REVIEW` instead of immediately generating audio. When the podcast has an active episode (status `GENERATING`, `PENDING_REVIEW`, or `APPROVED`), the service SHALL return `GenerateBriefingResult(episode = null)` and the controller SHALL return HTTP 200 with a message indicating no relevant articles to process.

#### Scenario: Trigger manual briefing without review
- **WHEN** a `POST /users/{userId}/podcasts/{podcastId}/generate` request is received for a podcast with `requireReview = false`
- **THEN** the system runs the LLM pipeline and TTS pipeline for that podcast and returns the result

#### Scenario: Trigger manual briefing with review
- **WHEN** a `POST /users/{userId}/podcasts/{podcastId}/generate` request is received for a podcast with `requireReview = true`
- **THEN** the system runs the LLM pipeline, saves the script as an episode with status `PENDING_REVIEW`, and returns HTTP 200 indicating a script is ready for review

#### Scenario: Trigger manual briefing when active episode exists
- **WHEN** a `POST /users/{userId}/podcasts/{podcastId}/generate` request is received and the podcast has an active episode (status `GENERATING`, `PENDING_REVIEW`, or `APPROVED`)
- **THEN** the service returns `GenerateBriefingResult(episode = null)` and the controller returns HTTP 200 with message "No relevant articles to process"

#### Scenario: Trigger manual briefing for non-existing podcast
- **WHEN** a `POST /users/{userId}/podcasts/{podcastId}/generate` request is received for a podcast that does not exist
- **THEN** the system returns HTTP 404

#### Scenario: Manual briefing with no relevant articles
- **WHEN** a `POST /users/{userId}/podcasts/{podcastId}/generate` request is received but the podcast has no relevant unprocessed articles
- **THEN** the system returns HTTP 200 with a message indicating no relevant articles to process

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
On application startup, the system SHALL find all episodes with status `GENERATING` or `GENERATING_AUDIO` and transition them to `FAILED` with error message "Generation interrupted by application restart".

#### Scenario: Stale GENERATING episodes on startup
- **WHEN** the application starts and there are episodes with status `GENERATING`
- **THEN** they are all updated to status `FAILED` with an appropriate error message

#### Scenario: Stale GENERATING_AUDIO episodes on startup
- **WHEN** the application starts and there are episodes with status `GENERATING_AUDIO`
- **THEN** they are all updated to status `FAILED` with error message "Generation interrupted by application restart"

