## ADDED Requirements

### Requirement: Regenerate audio for a GENERATED episode
The system SHALL allow a user to re-run TTS on a GENERATED episode's existing script without modifying the script, articles, or show notes. The action SHALL be available only when the episode status is `GENERATED`.

#### Scenario: Trigger audio regeneration
- **WHEN** a `POST` request is sent to `/users/{userId}/podcasts/{podcastId}/episodes/{episodeId}/regenerate-audio` for a GENERATED episode
- **THEN** the episode status SHALL transition to `GENERATING_AUDIO` and TTS regeneration SHALL start asynchronously

#### Scenario: Audio regeneration completes successfully
- **WHEN** TTS regeneration finishes without error
- **THEN** the episode status SHALL return to `GENERATED`, `audioFilePath` SHALL be updated to the new MP3 path, `durationSeconds` SHALL be updated, and `ttsModel` and `ttsCostCents` SHALL reflect the new run

#### Scenario: Audio regeneration fails
- **WHEN** TTS regeneration encounters an error
- **THEN** the episode status SHALL be set to `FAILED` with an appropriate `errorMessage`

#### Scenario: Attempt on non-GENERATED episode
- **WHEN** a `POST` request is sent for an episode whose status is NOT `GENERATED`
- **THEN** the system SHALL return HTTP 409 Conflict