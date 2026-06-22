## ADDED Requirements

### Requirement: TTS synthesis call count tracked and exposed
The system SHALL persist the number of TTS synthesis API calls made for an episode in a nullable `tts_calls` column on `episodes`. TTS synthesis splits the script into chunks (one API call per chunk, concatenated afterwards), so the call count SHALL equal the number of synthesized audio chunks (`ttsResult.audioChunks.size`). The value SHALL be written on both fresh generation and audio regeneration. The per-episode cost breakdown response SHALL expose this count as `costs.tts.calls`, defaulting to 0 when the column is null (legacy episodes generated before this column existed). TTS cost SHALL remain character-based and unaffected by the call count.

#### Scenario: Call count recorded for a multi-chunk episode
- **WHEN** an episode's script is synthesized in 7 chunks
- **THEN** the episode's `tts_calls` is 7 and the cost breakdown response has `costs.tts.calls = 7`

#### Scenario: Legacy episode without recorded call count
- **WHEN** an episode predates the `tts_calls` column and its value is null
- **THEN** the cost breakdown response has `costs.tts.calls = 0` (rendered as `—` in the UI)

#### Scenario: Call count does not affect TTS cost
- **WHEN** an episode of 10000 characters is synthesized in either 1 or 5 calls
- **THEN** the TTS cost is identical, computed only from the character total and `cost-per-million-chars`
