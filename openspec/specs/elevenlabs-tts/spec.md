# Capability: ElevenLabs TTS

## Purpose

Speech synthesis through ElevenLabs: the API client, the single-voice provider and the Text-to-Dialogue provider used for multi-speaker scripts.

## Requirements

### Requirement: ElevenLabs API client
The system SHALL provide an `ElevenLabsApiClient` component that wraps Spring's `RestClient` for calling ElevenLabs APIs. The client SHALL authenticate using the `xi-api-key` header with the API key resolved from the user's provider config. The base URL SHALL default to `https://api.elevenlabs.io`.

#### Scenario: API client authenticates with user's key
- **WHEN** the client makes a request to ElevenLabs
- **THEN** the `xi-api-key` header is set to the user's configured API key for the `elevenlabs` TTS provider

#### Scenario: API client uses configured base URL
- **WHEN** the user has a custom base URL configured for the `elevenlabs` provider
- **THEN** the client uses that base URL instead of the default

### Requirement: ElevenLabs single-voice TTS provider
The system SHALL implement `TtsProvider` as `ElevenLabsTtsProvider` that generates audio via the ElevenLabs `POST /v1/text-to-speech/{voice_id}` endpoint. The provider SHALL use the `"default"` key from `ttsVoices` to resolve the voice ID. The provider SHALL chunk text using the existing `TextChunker` and generate audio per chunk. Provider-specific settings from `ttsSettings` (e.g., `stability`, `similarity_boost`, `style`, `speed`) SHALL be passed as `voice_settings` in the API request. The output format SHALL be `mp3_44100_128`.

#### Scenario: Single-voice generation with default voice
- **WHEN** a podcast has `ttsVoices: {"default": "JBFqnCBsd6RMkjVDRZzb"}`
- **THEN** the provider calls `/v1/text-to-speech/JBFqnCBsd6RMkjVDRZzb` for each chunk

#### Scenario: Provider applies voice settings
- **WHEN** a podcast has `ttsSettings: {"stability": 0.5, "similarity_boost": 0.8}`
- **THEN** the API request body includes `voice_settings: {"stability": 0.5, "similarity_boost": 0.8}`

#### Scenario: Multiple chunks produce concatenation-required result
- **WHEN** the script is split into 3 chunks
- **THEN** the result contains 3 audio byte arrays with `requiresConcatenation = true`

#### Scenario: Missing default voice key
- **WHEN** a podcast has `ttsVoices` without a `"default"` key and style is not `"dialogue"`
- **THEN** the provider throws an error indicating the `"default"` voice must be configured

### Requirement: ElevenLabs Text-to-Dialogue provider
The system SHALL implement `TtsProvider` as `ElevenLabsDialogueTtsProvider` that generates multi-speaker audio via the ElevenLabs `POST /v1/text-to-dialogue` endpoint. The provider SHALL parse the script's XML-style speaker tags (e.g., `<host>`, `<cohost>`) into an array of `{text, voice_id}` inputs. Tag names SHALL be mapped to voice IDs via the podcast's `ttsVoices` map. The model SHALL be `eleven_v3`. When the total text length of all inputs exceeds 5000 characters, the provider SHALL split the inputs into batches where each batch's total text length stays under 5000 characters. Turns SHALL NOT be split across batches. Each batch SHALL be sent as a separate API call. When multiple batches are produced, the result SHALL have `requiresConcatenation = true` and contain one audio chunk per batch. When only one batch is needed, the result SHALL have `requiresConcatenation = false` with a single audio chunk.

#### Scenario: Dialogue script parsed into inputs array
- **WHEN** a script contains `<host>Hello!</host><cohost>Hi there!</cohost>` and `ttsVoices` is `{"host": "id1", "cohost": "id2"}`
- **THEN** the API request body contains `inputs: [{text: "Hello!", voice_id: "id1"}, {text: "Hi there!", voice_id: "id2"}]`

#### Scenario: Short dialogue fits in single batch
- **WHEN** a dialogue script has total text length under 5000 characters
- **THEN** the provider makes a single API call and returns `requiresConcatenation = false`

#### Scenario: Long dialogue split into multiple batches
- **WHEN** a dialogue script has total text length of 9000 characters
- **THEN** the provider splits turns into batches under 5000 characters each, makes one API call per batch, and returns `requiresConcatenation = true` with one audio chunk per batch

#### Scenario: Batch boundary falls between turns
- **WHEN** turns are grouped into batches
- **THEN** each turn is entirely within one batch — no turn is split across batches

#### Scenario: Single turn under limit starts new batch
- **WHEN** adding a turn to the current batch would exceed 5000 characters
- **THEN** a new batch is started with that turn

#### Scenario: Unknown speaker tag
- **WHEN** the script contains a `<narrator>` tag but `ttsVoices` has no `"narrator"` key
- **THEN** the provider throws an error listing the available voice roles

#### Scenario: Emotion cues preserved in text
- **WHEN** a dialogue turn contains `<host>[cheerfully] Welcome back!</host>`
- **THEN** the text sent to the API includes the `[cheerfully]` cue: `"[cheerfully] Welcome back!"`

#### Scenario: Provider-specific settings applied
- **WHEN** `ttsSettings` contains `{"stability": 0.7}`
- **THEN** the API request body includes `settings: {"stability": 0.7}`

### Requirement: ElevenLabs dialogue script parsing
The system SHALL parse dialogue scripts with XML-style speaker tags into a structured list of dialogue turns. Each turn SHALL contain the speaker role (tag name) and the spoken text (tag content). The parser SHALL handle multi-line content within tags. Text outside of any tags SHALL be treated as an error and logged as a warning.

#### Scenario: Simple two-speaker dialogue parsed
- **WHEN** the script is `<host>Hello!</host>\n<cohost>Hi!</cohost>`
- **THEN** the parser returns `[{role: "host", text: "Hello!"}, {role: "cohost", text: "Hi!"}]`

#### Scenario: Multi-line content within tag
- **WHEN** a tag contains `<host>First line.\nSecond line.</host>`
- **THEN** the parsed text is `"First line.\nSecond line."`

#### Scenario: Text outside tags produces warning
- **WHEN** the script contains `Some intro text <host>Hello!</host>`
- **THEN** the intro text is logged as a warning and excluded from the dialogue turns

#### Scenario: Empty script produces empty list
- **WHEN** the script is empty
- **THEN** the parser returns an empty list

### Requirement: ElevenLabs API error handling
The system SHALL handle ElevenLabs API errors gracefully. HTTP 401 errors SHALL produce a clear message about invalid API key. HTTP 429 errors SHALL produce a message about rate limiting. Other errors SHALL include the HTTP status and response body in the log. All errors SHALL cause the episode generation to fail with a descriptive error.

#### Scenario: Invalid API key
- **WHEN** the ElevenLabs API returns HTTP 401
- **THEN** the error message indicates the API key is invalid or expired

#### Scenario: Rate limited
- **WHEN** the ElevenLabs API returns HTTP 429
- **THEN** the error message indicates rate limiting and suggests retrying later

#### Scenario: Server error
- **WHEN** the ElevenLabs API returns HTTP 500
- **THEN** the error is logged with the response body and episode generation fails
