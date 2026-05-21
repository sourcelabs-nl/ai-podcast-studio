## ADDED Requirements

### Requirement: Stream episode MP3 via HTTP
The system SHALL expose a streaming HTTP endpoint that returns the episode's MP3 audio file. The endpoint SHALL validate that the episode belongs to the requested podcast and user.

#### Scenario: Stream audio for a GENERATED episode
- **WHEN** a `GET` request is sent to `/users/{userId}/podcasts/{podcastId}/episodes/{episodeId}/audio`
- **THEN** the system SHALL respond with HTTP 200, `Content-Type: audio/mpeg`, and the MP3 file contents streamed in the response body

#### Scenario: Episode has no audio file
- **WHEN** a `GET` request is sent for an episode with no `audioFilePath` (e.g., status is PENDING_REVIEW)
- **THEN** the system SHALL return HTTP 404 Not Found

#### Scenario: Audio file missing from disk
- **WHEN** a `GET` request is sent but the file at `audioFilePath` does not exist on disk
- **THEN** the system SHALL return HTTP 404 Not Found

### Requirement: Play button in episode table
The episode table SHALL display a play icon-link for each episode that has audio (`audioFilePath` is set). Clicking the link SHALL open the audio stream endpoint in a new browser tab.

#### Scenario: Episode has audio
- **WHEN** the episode table row is rendered for a GENERATED episode
- **THEN** a play icon-link SHALL be visible and SHALL link to the `/audio` endpoint

#### Scenario: Episode has no audio
- **WHEN** the episode table row is rendered for an episode without audio
- **THEN** no play icon-link SHALL be shown

### Requirement: Play button in episode detail page
The episode detail page SHALL display a play icon-link when the episode has audio. The link SHALL be shown in the header area alongside other episode metadata.

#### Scenario: Episode has audio
- **WHEN** the episode detail page is rendered for a GENERATED episode
- **THEN** a play icon-link SHALL be visible and SHALL link to the `/audio` endpoint

#### Scenario: Episode has no audio
- **WHEN** the episode detail page is rendered for an episode without audio
- **THEN** no play icon-link SHALL be shown

### Requirement: Regenerate audio button in episode detail page
The episode detail page SHALL display a "Regenerate Audio" action button when the episode status is `GENERATED`.

#### Scenario: Episode is GENERATED
- **WHEN** the episode detail page is rendered for a GENERATED episode
- **THEN** a "Regenerate Audio" button SHALL be visible in the actions area

#### Scenario: Regenerate audio button pressed
- **WHEN** the user clicks "Regenerate Audio"
- **THEN** the frontend SHALL call `POST .../regenerate-audio` and the UI SHALL update to reflect `GENERATING_AUDIO` status

### Requirement: Regenerate audio button in episode table
The episode table SHALL display a "Regenerate Audio" action button for GENERATED episodes, consistent with other per-row action buttons.

#### Scenario: Episode is GENERATED in table
- **WHEN** the episode table row is rendered for a GENERATED episode
- **THEN** a "Regenerate Audio" button SHALL be visible in the Actions column