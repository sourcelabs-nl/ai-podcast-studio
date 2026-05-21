## MODIFIED Requirements

### Requirement: Briefing generation uses per-podcast schedule
The `BriefingGenerationScheduler` SHALL launch a coroutine loop on `ApplicationReadyEvent` that runs every 60 seconds, using its own `CoroutineScope(Dispatchers.IO + SupervisorJob())` so it runs independently from other schedulers (e.g., source polling) and cannot be starved by them. The scope SHALL be cancelled on `@PreDestroy` for graceful shutdown. Each iteration SHALL query all podcasts, evaluate each podcast's `cron` expression against the current time and `last_generated_at`, and trigger the pipeline for podcasts that are due. The scheduler SHALL skip generation for any podcast that has an active episode (status `GENERATING`, `PENDING_REVIEW`, or `APPROVED`). The scheduler SHALL skip any cron trigger whose scheduled date (in the podcast's timezone) is before today, logging a WARN-level message for each skipped trigger. The scheduler SHALL advance through all stale triggers to find the next actionable one without modifying `last_generated_at`.

#### Scenario: Scheduled briefing respects individual cron expressions
- **WHEN** the scheduler runs and podcast A (cron: daily 06:00, last generated yesterday) and podcast B (cron: daily 18:00, last generated today at 18:00) exist, and it is currently 06:05
- **THEN** the system triggers the pipeline for podcast A only

#### Scenario: Scheduled briefing with no podcasts
- **WHEN** the scheduler runs and no podcasts exist
- **THEN** the system completes without generating any episodes

#### Scenario: Newly created podcast runs on first check
- **WHEN** the scheduler runs and a podcast has `last_generated_at` set to null and its cron expression indicates it should have run already today
- **THEN** the system triggers the pipeline for that podcast

#### Scenario: Scheduled briefing skips when active episode exists
- **WHEN** the scheduler runs and a podcast is due for generation, but an episode with status `GENERATING`, `PENDING_REVIEW`, or `APPROVED` already exists for that podcast
- **THEN** the system skips generation for that podcast (regardless of the podcast's `requireReview` setting)

#### Scenario: Scheduled briefing generates when no active episode
- **WHEN** the scheduler runs and a podcast is due for generation, and no episode with status `GENERATING`, `PENDING_REVIEW`, or `APPROVED` exists for that podcast
- **THEN** the system triggers the pipeline for that podcast

#### Scenario: Same-day catch-up fires when app comes back online
- **WHEN** the scheduler runs and a podcast (cron: daily 15:00, last generated yesterday at 15:00) exists, and the current time is today at 18:00 (3 hours past the trigger, but same calendar day in the podcast's timezone)
- **THEN** the system triggers the pipeline for that podcast

#### Scenario: Trigger within minutes of scheduled time is executed
- **WHEN** the scheduler runs and a podcast (cron: daily 15:00, last generated yesterday at 15:00) exists, and the current time is today at 15:10 (10 minutes past the trigger)
- **THEN** the system triggers the pipeline for that podcast

#### Scenario: Previous-day triggers are skipped
- **WHEN** the scheduler runs and a podcast (cron: daily 15:00, last generated 3 days ago) exists, and the current time is today at 10:00
- **THEN** the system skips all 3 missed triggers from previous days (each logged at WARN level) and waits for today's 15:00 trigger

#### Scenario: Skipped triggers do not update last_generated_at
- **WHEN** the scheduler skips a stale trigger for a podcast
- **THEN** the podcast's `last_generated_at` remains unchanged

#### Scenario: Same-day catch-up respects timezone
- **WHEN** the scheduler runs and a podcast (cron: daily 23:00, timezone: Europe/Amsterdam, last generated yesterday) exists, and the current UTC time is 01:30 (which is 03:30 in Amsterdam, the next day)
- **THEN** the system skips the trigger because it falls on the previous calendar day in the podcast's timezone
