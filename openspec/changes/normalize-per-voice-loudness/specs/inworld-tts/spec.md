## MODIFIED Requirements

### Requirement: Inworld AI TTS provider
The system SHALL provide an `InworldTtsProvider` implementing `TtsProvider` that generates audio via the Inworld AI TTS API. The provider SHALL send text to `POST https://api.inworld.ai/tts/v1/voice` with the configured `voiceId` and `modelId`. The response SHALL contain base64-encoded audio in `audioContent` and character usage in `usage.processedCharactersCount`. The provider SHALL decode the base64 audio into byte arrays, each tagged with the voice that produced it. The default model SHALL be `inworld-tts-2`, overridable via `ttsSettings["model"]`. Supported models include `inworld-tts-1.5-max`, `inworld-tts-1.5-mini`, and `inworld-tts-2`. The audio format SHALL be MP3 at 48000 Hz sample rate and 128000 bps bit rate, requested via `audioConfig.sampleRateHertz` and `audioConfig.bitRate`. The provider SHALL use `TextChunker` with its `maxChunkSize` of 1900 to split long scripts. The provider SHALL generate all chunks concurrently using Kotlin coroutines (`async`/`awaitAll` on `Dispatchers.IO`). Chunk ordering in the result SHALL match the original script order regardless of completion order.

The provider SHALL accept an optional `ttsSettings["deliveryMode"]` value (case-insensitive, blank treated as unset) that maps to Inworld's TTS-2 `deliveryMode` enum with values `STABLE`, `BALANCED`, or `CREATIVE`. `EXPRESSIVE` is NOT a value of this enum and SHALL NOT appear in the backend, the settings UI, or stored settings. When `deliveryMode` is set, the provider SHALL forward it as the `deliveryMode` field in the synthesize request body and SHALL NOT send `temperature` (the Inworld API treats them as mutually exclusive on TTS-2). When `deliveryMode` is unset, the provider SHALL fall back to `temperature` behavior: explicit `ttsSettings["temperature"]` if present, otherwise the default `0.8`.

The provider SHALL accept an optional `ttsSettings["enhanceGeneration"]` value parsed strictly as a boolean (only `"true"` and `"false"` are recognised; any other value SHALL be treated as unset). When set, the provider SHALL forward it as the top-level `enhanceGeneration` field in the synthesize request body, enabling Inworld's denoising of the synthesized audio. When unset, the field SHALL be omitted so the API applies its own default. Enabling `enhanceGeneration` SHALL NOT change the sample rate, channel count, or bit rate of the returned audio.

The provider SHALL pass optional knobs (speed, temperature, deliveryMode, enhanceGeneration, language, previousRequests) to `InworldApiClient.synthesizeSpeech` as a single `InworldSynthesisOptions` data class rather than as separate positional arguments.

#### Scenario: Single chunk generates audio
- **WHEN** a script of 1500 characters is sent to the Inworld provider
- **THEN** the provider makes one API call and returns a `TtsResult` with one audio chunk

#### Scenario: Long script chunked and generated in parallel
- **WHEN** a script of 5000 characters is sent to the Inworld provider
- **THEN** the provider splits it into 3+ chunks via `TextChunker`, generates all chunks concurrently via coroutines, and returns a `TtsResult` with audio chunks in script order

#### Scenario: Model override via ttsSettings
- **WHEN** a podcast has `ttsSettings: {"model": "inworld-tts-1.5-mini"}`
- **THEN** the Inworld API is called with `modelId: "inworld-tts-1.5-mini"`

#### Scenario: Default model used when not specified
- **WHEN** a podcast has no `"model"` key in `ttsSettings`
- **THEN** the Inworld API is called with `modelId: "inworld-tts-2"`

#### Scenario: Bit rate requested using the documented field name
- **WHEN** any TTS request is sent to the Inworld API
- **THEN** the request includes `audioConfig.bitRate: 128000` (bits per second) and does NOT include `audioConfig.bitRateHertz`, which the API does not recognise and would silently ignore

#### Scenario: Temperature setting applied via ttsSettings
- **WHEN** a podcast has `ttsSettings: {"temperature": "0.8"}` and no `"deliveryMode"` key
- **THEN** the Inworld API request includes `temperature: 0.8` as a top-level field

#### Scenario: Default temperature of 0.8 when not configured
- **WHEN** a podcast has no `"temperature"` and no `"deliveryMode"` key in `ttsSettings`
- **THEN** the Inworld API request includes `temperature: 0.8`

#### Scenario: deliveryMode applied via ttsSettings
- **WHEN** a podcast has `ttsSettings: {"model": "inworld-tts-2", "deliveryMode": "CREATIVE"}`
- **THEN** the Inworld API request body includes `"deliveryMode": "CREATIVE"` and does NOT include the `temperature` field

#### Scenario: deliveryMode is uppercase-normalized
- **WHEN** a podcast has `ttsSettings: {"deliveryMode": "creative"}`
- **THEN** the Inworld API request body includes `"deliveryMode": "CREATIVE"`

#### Scenario: Blank deliveryMode is treated as unset
- **WHEN** a podcast has `ttsSettings: {"deliveryMode": ""}`
- **THEN** the Inworld API request omits `deliveryMode` and applies the default temperature `0.8`

#### Scenario: deliveryMode suppresses both default and explicit temperature
- **WHEN** a podcast has `ttsSettings: {"deliveryMode": "STABLE", "temperature": "1.1"}`
- **THEN** the Inworld API request body includes `"deliveryMode": "STABLE"` and does NOT include `temperature`

#### Scenario: Enhanced audio quality enabled via ttsSettings
- **WHEN** a podcast has `ttsSettings: {"enhanceGeneration": "true"}`
- **THEN** the Inworld API request body includes `"enhanceGeneration": true`

#### Scenario: Enhanced audio quality explicitly disabled
- **WHEN** a podcast has `ttsSettings: {"enhanceGeneration": "false"}`
- **THEN** the Inworld API request body includes `"enhanceGeneration": false`

#### Scenario: enhanceGeneration omitted when not configured
- **WHEN** a podcast has no `"enhanceGeneration"` key in `ttsSettings`
- **THEN** the Inworld API request body does not include `enhanceGeneration`

#### Scenario: Non-boolean enhanceGeneration is treated as unset
- **WHEN** a podcast has `ttsSettings: {"enhanceGeneration": "yes"}`
- **THEN** the Inworld API request body does not include `enhanceGeneration`

#### Scenario: Enhanced audio quality preserves the audio encoding
- **WHEN** a TTS request is sent with `enhanceGeneration: true`
- **THEN** the returned audio has the same sample rate, channel count, and bit rate as the same request without `enhanceGeneration`, so concatenation by stream copy remains valid

#### Scenario: Speed omitted when not configured
- **WHEN** a podcast has no `"speed"` key in `ttsSettings`
- **THEN** the Inworld API request does not include `speakingRate` in `audioConfig`, letting the API use its default

#### Scenario: Voice resolved from ttsVoices map
- **WHEN** a podcast has `ttsVoices: {"default": "some-inworld-voice-id"}`
- **THEN** the Inworld API is called with `voiceId: "some-inworld-voice-id"`

#### Scenario: Character count tracked from API response
- **WHEN** the Inworld API returns `usage.processedCharactersCount: 1800`
- **THEN** the `TtsResult.totalCharacters` reflects the sum of processed characters across all chunks

#### Scenario: Dialogue chunks generated in parallel
- **WHEN** a dialogue script with 4 turns (total 6000 chars) is sent to the Inworld provider
- **THEN** the provider flattens all turn chunks into a single list and generates them all concurrently, returning audio chunks in original turn/chunk order
