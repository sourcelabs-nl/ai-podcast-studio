## MODIFIED Requirements

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
- **THEN** the result contains 3 audio chunks, each naming the configured voice

#### Scenario: Missing default voice key
- **WHEN** a podcast has `ttsVoices` without a `"default"` key and style is not `"dialogue"`
- **THEN** the provider throws an error indicating the `"default"` voice must be configured

### Requirement: ElevenLabs Text-to-Dialogue provider
The system SHALL implement `TtsProvider` as `ElevenLabsDialogueTtsProvider` that generates multi-speaker audio via the ElevenLabs `POST /v1/text-to-dialogue` endpoint. The provider SHALL parse the script's XML-style speaker tags (e.g., `<host>`, `<cohost>`) into an array of `{text, voice_id}` inputs. Tag names SHALL be mapped to voice IDs via the podcast's `ttsVoices` map. The model SHALL be `eleven_v3`. When the total text length of all inputs exceeds 5000 characters, the provider SHALL split the inputs into batches where each batch's total text length stays under 5000 characters. Turns SHALL NOT be split across batches. Each batch SHALL be sent as a separate API call. The result SHALL contain one audio chunk per batch. Because a batch carries several speakers in a single chunk, already balanced against each other by ElevenLabs, every such chunk SHALL be tagged as mixed rather than naming one speaker; the concatenator then levels those chunks as one group.

#### Scenario: Dialogue script parsed into inputs array
- **WHEN** a script contains `<host>Hello!</host><cohost>Hi there!</cohost>` and `ttsVoices` is `{"host": "id1", "cohost": "id2"}`
- **THEN** the API request body contains `inputs: [{text: "Hello!", voice_id: "id1"}, {text: "Hi there!", voice_id: "id2"}]`

#### Scenario: Short dialogue fits in single batch
- **WHEN** a dialogue script has total text length under 5000 characters
- **THEN** the provider makes a single API call and returns one audio chunk

#### Scenario: Long dialogue split into multiple batches
- **WHEN** a dialogue script has total text length of 9000 characters
- **THEN** the provider splits turns into batches under 5000 characters each, makes one API call per batch, and returns one audio chunk per batch

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
