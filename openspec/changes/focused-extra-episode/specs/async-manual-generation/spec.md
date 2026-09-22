## MODIFIED Requirements

### Requirement: Manual generation runs in the background
The system SHALL run manual episode generation (`POST /generate`) and regeneration (`POST /regenerate`) as background jobs on a managed coroutine scope, decoupled from the HTTP request lifecycle, so that a request timeout or client disconnect cannot cancel the in-flight pipeline. `POST /generate` SHALL accept an optional `focus` string in its request body; when present and non-blank it starts a focus episode (see `focus-episode-generation`), otherwise generation is a regular episode exactly as before.

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

#### Scenario: Generate with a focus field starts a focus episode
- **WHEN** a user triggers `POST /generate` with body `{"focus": "Claude Opus 5.5 release"}`
- **THEN** the endpoint responds `202 Accepted` with the new GENERATING episode id, and that episode is generated as a focus episode

#### Scenario: Generate without a focus field is unaffected
- **WHEN** a user triggers `POST /generate` with no body or an empty `focus`
- **THEN** generation proceeds exactly as it did before focus episodes existed
