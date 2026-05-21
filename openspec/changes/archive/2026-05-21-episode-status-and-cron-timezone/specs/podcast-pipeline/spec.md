## MODIFIED Requirements

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

### Requirement: Startup cleanup of stale GENERATING episodes
On application startup, the system SHALL find all episodes with status `GENERATING` or `GENERATING_AUDIO` and transition them to `FAILED` with error message "Generation interrupted by application restart".

#### Scenario: Stale GENERATING episodes on startup
- **WHEN** the application starts and there are episodes with status `GENERATING`
- **THEN** they are all updated to status `FAILED` with an appropriate error message

#### Scenario: Stale GENERATING_AUDIO episodes on startup
- **WHEN** the application starts and there are episodes with status `GENERATING_AUDIO`
- **THEN** they are all updated to status `FAILED` with error message "Generation interrupted by application restart"