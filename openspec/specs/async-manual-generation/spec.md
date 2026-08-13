# async-manual-generation Specification

## Purpose
Manually triggered generation runs in the background so the request returns immediately, with conflict handling when a run is already in progress and no effect on the podcast's schedule.
## Requirements
### Requirement: Manual generation runs in the background
The system SHALL run manual episode generation (`POST /generate`) and regeneration (`POST /regenerate`) as background jobs on a managed coroutine scope, decoupled from the HTTP request lifecycle, so that a request timeout or client disconnect cannot cancel the in-flight pipeline.

#### Scenario: Generate returns immediately and continues in the background
- **WHEN** a user triggers manual generation for a podcast with no active episode
- **THEN** the endpoint responds `202 Accepted` with the new GENERATING episode id
- **AND** the generation pipeline continues to run after the response is sent, emitting progress via SSE, even if the original request connection is closed

#### Scenario: Regenerate returns immediately with the new episode id
- **WHEN** a user triggers regeneration of an existing episode
- **THEN** the endpoint responds `202 Accepted` with a new GENERATING episode id
- **AND** the recompose and TTS work runs in the background

#### Scenario: Generation is not cancelled by the request timeout
- **WHEN** the generation pipeline runs longer than the HTTP async-request timeout
- **THEN** the episode still completes (or fails on its own error) because the work is not tied to the request

### Requirement: Conflict when an episode is already generating
The `POST /generate` endpoint SHALL reject a manual trigger when an episode is already active for the podcast, rather than starting a second concurrent generation.

#### Scenario: Concurrent generate is rejected
- **WHEN** a user triggers manual generation while an episode is already generating for that podcast
- **THEN** the endpoint responds `409 Conflict` and no new generation is started

### Requirement: Regeneration does not advance the schedule
Regeneration SHALL NOT update the podcast's `lastGeneratedAt`, so the scheduler still runs the next scheduled generation as planned.

#### Scenario: Regenerate preserves the cron schedule
- **WHEN** a user regenerates an existing episode
- **THEN** the podcast's `lastGeneratedAt` is unchanged and the next scheduled generation is unaffected

