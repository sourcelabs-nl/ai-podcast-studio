# Capability: Inworld TTS

## Purpose

Inworld AI TTS provider integration including model selection, delivery mode, expressiveness, and voice discovery.
## Requirements
### Requirement: Inworld AI TTS provider
The system SHALL provide an `InworldTtsProvider` implementing `TtsProvider` that generates audio via the Inworld AI TTS API. The provider SHALL send text to `POST https://api.inworld.ai/tts/v1/voice` with the configured `voiceId` and `modelId`. The response SHALL contain base64-encoded audio in `audioContent` and character usage in `usage.processedCharactersCount`. The provider SHALL decode the base64 audio into byte arrays. The default model SHALL be `inworld-tts-2`, overridable via `ttsSettings["model"]`. Supported models include `inworld-tts-1.5-max`, `inworld-tts-1.5-mini`, and `inworld-tts-2`. The audio format SHALL be MP3 at 48000 Hz sample rate and 128000 bps bit rate, requested via `audioConfig.sampleRateHertz` and `audioConfig.bitRate`. The provider SHALL use `TextChunker` with its `maxChunkSize` of 1900 to split long scripts. The result SHALL have `requiresConcatenation = true` when multiple chunks are generated. The provider SHALL generate all chunks concurrently using Kotlin coroutines (`async`/`awaitAll` on `Dispatchers.IO`). Chunk ordering in the result SHALL match the original script order regardless of completion order.

The provider SHALL accept an optional `ttsSettings["deliveryMode"]` value (case-insensitive, blank treated as unset) that maps to Inworld's TTS-2 `deliveryMode` enum with values `STABLE`, `BALANCED`, or `CREATIVE`. `EXPRESSIVE` is NOT a value of this enum and SHALL NOT appear in the backend, the settings UI, or stored settings. When `deliveryMode` is set, the provider SHALL forward it as the `deliveryMode` field in the synthesize request body and SHALL NOT send `temperature` (the Inworld API treats them as mutually exclusive on TTS-2). When `deliveryMode` is unset, the provider SHALL fall back to `temperature` behavior: explicit `ttsSettings["temperature"]` if present, otherwise the default `0.8`.

The provider SHALL accept an optional `ttsSettings["enhanceGeneration"]` value parsed strictly as a boolean (only `"true"` and `"false"` are recognised; any other value SHALL be treated as unset). When set, the provider SHALL forward it as the top-level `enhanceGeneration` field in the synthesize request body, enabling Inworld's denoising of the synthesized audio. When unset, the field SHALL be omitted so the API applies its own default. Enabling `enhanceGeneration` SHALL NOT change the sample rate, channel count, or bit rate of the returned audio.

The provider SHALL pass optional knobs (speed, temperature, deliveryMode, enhanceGeneration, language, previousRequests) to `InworldApiClient.synthesizeSpeech` as a single `InworldSynthesisOptions` data class rather than as separate positional arguments.

#### Scenario: Single chunk generates audio
- **WHEN** a script of 1500 characters is sent to the Inworld provider
- **THEN** the provider makes one API call and returns a `TtsResult` with one audio chunk and `requiresConcatenation = false`

#### Scenario: Long script chunked and generated in parallel
- **WHEN** a script of 5000 characters is sent to the Inworld provider
- **THEN** the provider splits it into 3+ chunks via `TextChunker`, generates all chunks concurrently via coroutines, and returns a `TtsResult` with audio chunks in script order and `requiresConcatenation = true`

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

### Requirement: Inworld enhanced audio quality setting in the UI
The podcast settings page SHALL present an "Enhanced Audio Quality" switch whenever the podcast's TTS provider is Inworld. The switch SHALL be gated on the provider only and SHALL NOT be restricted to a specific Inworld model, since the API does not document `enhanceGeneration` as model-specific. Enabling the switch SHALL set `ttsSettings["enhanceGeneration"]` to `"true"`; disabling it SHALL remove the key from `ttsSettings` rather than setting it to `"false"`, so the provider default applies.

#### Scenario: Switch shown for Inworld podcasts
- **WHEN** a podcast's TTS provider is set to `inworld`
- **THEN** the settings page shows an "Enhanced Audio Quality" switch

#### Scenario: Switch hidden for other providers
- **WHEN** a podcast's TTS provider is set to `elevenlabs` or `openai`
- **THEN** the settings page does not show the "Enhanced Audio Quality" switch

#### Scenario: Enabling the switch persists the setting
- **WHEN** the user turns the "Enhanced Audio Quality" switch on and saves
- **THEN** the podcast's `ttsSettings` contains `"enhanceGeneration": "true"`

#### Scenario: Disabling the switch clears the setting
- **WHEN** the user turns the "Enhanced Audio Quality" switch off and saves
- **THEN** the podcast's `ttsSettings` no longer contains an `enhanceGeneration` key

### Requirement: Inworld API client with Basic authentication
The system SHALL provide an `InworldApiClient` that authenticates to the Inworld AI API using HTTP Basic authentication. The client SHALL accept credentials in the format `key:secret` (stored as the `apiKey` in provider config), base64-encode them, and send the result in the `Authorization` header as `Basic <base64-encoded-credentials>`. Per-user credentials SHALL be resolved via `UserProviderConfigService` with `ApiKeyCategory.TTS` and provider name `"inworld"`, falling back to the global env vars (`INWORLD_AI_JWT_KEY` and `INWORLD_AI_JWT_SECRET` combined as `key:secret`).

The HTTP client SHALL use a response timeout of 5 minutes to accommodate long TTS generation requests (Inworld may take over 30 seconds for large scripts).

The HTTP client SHALL use a dedicated Reactor Netty connection provider with a maximum idle time of 30 seconds, so that pooled keep-alive connections are evicted before Inworld's load balancer closes them server-side. Reusing a connection that the server has already closed results in a `Connection reset` failure.

#### Scenario: Authentication with per-user config
- **WHEN** a user has configured Inworld TTS provider credentials (e.g. via `PUT /users/{userId}/api-keys/tts` with provider `"inworld"` and apiKey `"my-key:my-secret"`)
- **THEN** the client base64-encodes the credentials and sends them as `Authorization: Basic <token>`

#### Scenario: Authentication with global env vars
- **WHEN** no per-user Inworld config exists and `INWORLD_AI_JWT_KEY` and `INWORLD_AI_JWT_SECRET` env vars are set
- **THEN** the client uses the global credentials (combined as `key:secret`) to authenticate

#### Scenario: Missing credentials
- **WHEN** neither per-user config nor global env vars provide Inworld credentials
- **THEN** the client throws an `IllegalStateException` with a message indicating Inworld API credentials must be configured

#### Scenario: Response timeout for long scripts
- **WHEN** the Inworld API takes longer than the default HTTP client timeout (e.g. 30+ seconds for a large dialogue script)
- **THEN** the request does not time out because the client uses a 5-minute response timeout

#### Scenario: Idle pooled connections are evicted
- **WHEN** a pooled connection to the Inworld API has been idle for more than 30 seconds
- **THEN** the connection provider evicts it so a subsequent request opens a fresh connection instead of reusing a stale one

### Requirement: Inworld API error handling
The system SHALL handle Inworld API errors with clear error messages. HTTP 401 SHALL indicate invalid or expired credentials. HTTP 429 SHALL be retried with exponential backoff (up to 3 attempts with delays of 1s, 2s, 4s). If retries are exhausted, the system SHALL throw an `InworldRateLimitException`. HTTP 5xx (500–599) SHALL be treated as transient: it SHALL be retried with the same exponential backoff schedule (up to 3 attempts with delays of 1s, 2s, 4s), and if retries are exhausted the system SHALL throw an `InworldTransientException` including the HTTP status and response body. Other error status codes SHALL throw immediately with the HTTP status and response body in the error message.

Transient I/O failures (`ResourceAccessException`, e.g. connection reset or operation timed out) SHALL be retried with the same exponential backoff schedule (up to 3 attempts with delays of 1s, 2s, 4s). If retries are exhausted, the system SHALL rethrow the `ResourceAccessException`.

#### Scenario: Invalid credentials
- **WHEN** the Inworld API returns HTTP 401
- **THEN** the system throws an error indicating Inworld API credentials are invalid or expired

#### Scenario: Rate limit exceeded with successful retry
- **WHEN** the Inworld API returns HTTP 429 on the first attempt
- **AND** the retry succeeds on the second attempt
- **THEN** the system returns the successful response after a 1-second backoff delay

#### Scenario: Rate limit exceeded with exhausted retries
- **WHEN** the Inworld API returns HTTP 429 on all 3 retry attempts
- **THEN** the system throws an `InworldRateLimitException` indicating the rate limit was exceeded after retries

#### Scenario: Transient I/O error with successful retry
- **WHEN** the Inworld API request fails with a `ResourceAccessException` (e.g. `Connection reset`) on the first attempt
- **AND** the retry succeeds on the second attempt
- **THEN** the system returns the successful response after a 1-second backoff delay and logs a warning with the I/O error message

#### Scenario: Transient I/O error with exhausted retries
- **WHEN** the Inworld API request fails with a `ResourceAccessException` on all 3 retry attempts
- **THEN** the system rethrows the `ResourceAccessException` from the final attempt

#### Scenario: Transient 5xx error with successful retry
- **WHEN** the Inworld API returns HTTP 503 on the first attempt
- **AND** the retry succeeds on the second attempt
- **THEN** the system returns the successful response after a 1-second backoff delay and logs a warning

#### Scenario: Transient 5xx error with exhausted retries
- **WHEN** the Inworld API returns HTTP 503 on all 3 retry attempts
- **THEN** the system throws an `InworldTransientException` including the HTTP status and response body

#### Scenario: Non-retryable client error
- **WHEN** the Inworld API returns a non-401, non-429 HTTP 4xx error
- **THEN** the system logs the error body and throws an error with the HTTP status and body (no retry)

#### Scenario: Partial failure in parallel generation
- **WHEN** 6 chunks are generated in parallel and 1 chunk fails after retry exhaustion
- **THEN** the entire generation fails with the error from the failed chunk

### Requirement: Inworld API sends applyTextNormalization
The `InworldApiClient.synthesizeSpeech()` SHALL always include `applyTextNormalization: "ON"` in the request body. This enables Inworld's built-in text normalization as a safety net for numbers, dates, and currencies that the LLM may not have expanded to spoken form.

#### Scenario: Text normalization enabled in API request
- **WHEN** a TTS request is sent to the Inworld API
- **THEN** the request body includes `"applyTextNormalization": "ON"`

### Requirement: Inworld TTS script guidelines
The `InworldTtsProvider` SHALL return style-aware script guidelines via `scriptGuidelines(style, pronunciations)`. The guidelines SHALL instruct the LLM to use Inworld-specific markup:
- Non-verbal tags: `[sigh]`, `[laugh]`, `[breathe]`, `[cough]`, `[clear throat]`, `[yawn]` — spelled exactly as Inworld documents them, with a space rather than an underscore in `[clear throat]`
- Emphasis: `*word*` (single asterisks) for stressed words, or CAPS for a whole word or a single syllable (`AbsoLUTEly`)
- Pacing: ellipsis (`...`) for trailing pauses, exclamation marks for excitement
- Pauses: SSML break tags such as `<break time="1s" />` for a deliberate beat between segments, at most 20 per request and at most 10 seconds each, and not immediately before a paragraph break where the pause already exists
- Steering: at most one short free-form English delivery instruction in square brackets (for example `[warm and conversational with an easy pace]`) at the start of a speaker turn or segment, with `[reset]` to return to neutral delivery
- Acronyms: expand on first use, then use the short form — spoken as a word when pronounceable and spelled out letter by letter when not, because Inworld's normalization does not cover domain acronyms
- IPA phonemes: `/phoneme/` for precise pronunciation of proper nouns

The guidelines SHALL additionally include:
- Text normalization: write all numbers, dates, currencies, and symbols in fully spoken form
- Anti-markdown: never use markdown formatting; never use `**double asterisks**` as the TTS engine reads asterisk characters aloud
- Contractions: use natural contractions throughout for spoken naturalness
- Punctuation: always end sentences with proper punctuation for correct pacing

The steering guidance SHALL be emitted for every style and model. The post-processor is responsible for stripping steering instructions on models that do not support them, so the guidelines do not need a model parameter.

For `CASUAL`, `DIALOGUE` and `INTERVIEW` styles, guidelines SHALL additionally encourage natural filler words (`uh`, `um`, `well`, `you know`), because disfluencies are what make synthesised conversational speech sound human. For `EXECUTIVE_SUMMARY` and `NEWS_BRIEFING` styles, guidelines SHALL instruct to avoid filler words and minimize non-verbal tags.

When `pronunciations` is non-empty, the guidelines SHALL append a "Pronunciation Guide" section listing each term and its IPA phoneme. The guidelines SHALL instruct the LLM to REPLACE the word with its IPA phoneme notation on every occurrence (not write both the word and the phoneme), and to ONLY use IPA for the listed terms (never invent IPA for unlisted words). When `pronunciations` is empty, no pronunciation section SHALL be appended.

#### Scenario: Casual style guidelines include filler words
- **WHEN** `scriptGuidelines(PodcastStyle.CASUAL, emptyMap())` is called
- **THEN** the returned text includes instructions to use filler words naturally

#### Scenario: Interview style guidelines include filler words
- **WHEN** `scriptGuidelines(PodcastStyle.INTERVIEW, emptyMap())` is called
- **THEN** the returned text includes instructions to use filler words naturally and does not instruct to avoid them

#### Scenario: Executive summary guidelines suppress filler words
- **WHEN** `scriptGuidelines(PodcastStyle.EXECUTIVE_SUMMARY, emptyMap())` is called
- **THEN** the returned text instructs to avoid filler words and minimize non-verbal tags

### Requirement: Inworld TTS max chunk size
The `InworldTtsProvider` SHALL declare `maxChunkSize = 1900`, leaving headroom below the Inworld API's 2000 character per-request limit for a steering instruction prepended by the provider.

#### Scenario: Max chunk size is 1900
- **WHEN** the Inworld provider's `maxChunkSize` is queried
- **THEN** it returns 1900

### Requirement: Inworld voice discovery
The system SHALL provide voice listing for Inworld AI via the `InworldApiClient`. The client SHALL call the Inworld voices API and return a list of `VoiceInfo` objects with `voiceId`, `name`, `category`, and `previewUrl`.

#### Scenario: List Inworld voices
- **WHEN** `listVoices(userId)` is called on `InworldApiClient`
- **THEN** the client calls the Inworld voices API and returns available voices

#### Scenario: Inworld voices API error
- **WHEN** the Inworld voices API returns an error
- **THEN** the error is handled with the same pattern as other Inworld API errors

### Requirement: Inworld TTS provider type
The `TtsProviderType` enum SHALL include an `INWORLD` entry with value `"inworld"`.

#### Scenario: Inworld provider type serialization
- **WHEN** a podcast has `ttsProvider: TtsProviderType.INWORLD`
- **THEN** it serializes to JSON as `"inworld"`

#### Scenario: Inworld provider type deserialization
- **WHEN** a JSON payload contains `"ttsProvider": "inworld"`
- **THEN** it deserializes to `TtsProviderType.INWORLD`

### Requirement: Inworld synthesis context from preceding chunks
The `InworldTtsProvider` SHALL send `synthesisContext.previousRequests` with each synthesis call, carrying the text of the chunks that immediately precede it in the same script, oldest first. For monologue styles the preceding chunks are the preceding chunks of the script; for dialogue and interview styles they are the preceding chunks across all turns in conversation order, so a short turn is contextualised by the turn before it.

The window SHALL be bounded: at most 3 preceding texts and at most 2000 characters in total, whichever binds first, counted backwards from the current chunk. A preceding text that does not fit within the remaining character budget SHALL be omitted along with everything older than it. The first chunk of a script SHALL have no context.

`InworldApiClient.buildSynthesisBody` SHALL emit the window as `{"synthesisContext": {"previousRequests": [{"text": "..."}, ...]}}` and SHALL omit the `synthesisContext` field entirely when the window is empty.

Sending context SHALL NOT reduce parallelism: because the whole script is chunked before generation, every chunk's predecessors are known up front and all chunks are still generated concurrently.

#### Scenario: First chunk has no context
- **WHEN** the first chunk of a script is synthesized
- **THEN** the request body does not include a `synthesisContext` field

#### Scenario: Later chunk carries its predecessors
- **WHEN** the third chunk of a monologue script is synthesized
- **THEN** the request body includes `synthesisContext.previousRequests` with the text of chunks one and two, in that order

#### Scenario: Context window is capped at three texts
- **WHEN** the sixth chunk of a script is synthesized and all chunks are short
- **THEN** `synthesisContext.previousRequests` contains exactly the three immediately preceding chunk texts

#### Scenario: Context window is capped by total characters
- **WHEN** the preceding chunks together exceed 2000 characters
- **THEN** the oldest texts are dropped until the window fits within 2000 characters

#### Scenario: Dialogue turns carry the preceding turn as context
- **WHEN** a dialogue script's second turn is synthesized
- **THEN** its request includes the text of the first turn's last chunk in `synthesisContext.previousRequests`

#### Scenario: Context does not serialize generation
- **WHEN** a script producing 5 chunks is generated
- **THEN** all 5 chunks are still dispatched concurrently

### Requirement: Inworld steering instructions with per-chunk re-emission
Inworld steering instructions SHALL be sent only on `inworld-tts-2`, the only model that supports them. A steering instruction is a bracketed free-form English tag that is not one of the documented sound names, for example `[warm and conversational with an easy pace]`. An instruction stays in force until it is changed or cleared with `[reset]`, but only within a single request.

Because the provider splits a turn into multiple requests, the provider SHALL track the active instruction across the chunks of a turn and SHALL prepend it to the head of any subsequent chunk that does not already begin with an instruction, so the direction is not silently lost at a splice point. `[reset]` SHALL clear the active instruction. Sound tags SHALL NOT change the active instruction. Tracking SHALL restart at the beginning of each dialogue turn, so a direction given to one speaker does not carry into another speaker's voice.

Re-emission SHALL be applied only when the model supports steering.

#### Scenario: Instruction re-emitted on the following chunk
- **WHEN** a turn opens with `[warm and conversational]` and is split into three chunks on `inworld-tts-2`
- **THEN** chunks two and three are each prefixed with `[warm and conversational]`

#### Scenario: A new instruction replaces the active one
- **WHEN** a later chunk contains `[brisk and urgent]`
- **THEN** the chunks after it are prefixed with `[brisk and urgent]` rather than the earlier instruction

#### Scenario: Reset clears the active instruction
- **WHEN** a chunk contains `[reset]`
- **THEN** the chunks after it are not prefixed with any instruction

#### Scenario: A chunk that already opens with an instruction is not prefixed
- **WHEN** a chunk already begins with a bracketed instruction
- **THEN** the provider does not prepend the previously active instruction to it

#### Scenario: Sound tags do not become the active instruction
- **WHEN** a chunk contains `[sigh]` and no steering instruction
- **THEN** no instruction is prepended to the following chunks

#### Scenario: Steering not applied on models without support
- **WHEN** the model is `inworld-tts-1.5-max`
- **THEN** no instruction is prepended to any chunk, and steering tags are stripped by the post-processor before chunking

#### Scenario: Instructions do not leak across dialogue turns
- **WHEN** the host's turn sets `[excited and fast]` and the co-host's turn sets no instruction
- **THEN** the co-host's chunks are not prefixed with `[excited and fast]`

### Requirement: Inworld language field
The `InworldTtsProvider` SHALL send the podcast's configured language as the top-level `language` field of the synthesize request, so a localized voice prompt is used instead of relying on auto-detection. The podcast's ISO 639-1 code is already a well-formed BCP-47 language tag and SHALL be sent unchanged, without inventing a region subtag. A blank language SHALL result in the field being omitted.

#### Scenario: Language forwarded from the podcast
- **WHEN** a podcast with `language: "nl"` generates audio via Inworld
- **THEN** the request body includes `"language": "nl"`

#### Scenario: Blank language omitted
- **WHEN** the resolved language is blank
- **THEN** the request body does not include a `language` field

### Requirement: Inworld speaking rate clamping
`InworldApiClient.buildSynthesisBody` SHALL clamp `audioConfig.speakingRate` to the documented range `[0.5, 1.5]` and SHALL log a warning when a configured value is clamped. A value inside the range but below the recommended `0.8` SHALL be sent unchanged with a warning, because it is legal but degrades quality.

#### Scenario: Value above the range is clamped
- **WHEN** `ttsSettings: {"speed": "2.0"}`
- **THEN** the request includes `audioConfig.speakingRate: 1.5` and a warning is logged

#### Scenario: Value below the range is clamped
- **WHEN** `ttsSettings: {"speed": "0.2"}`
- **THEN** the request includes `audioConfig.speakingRate: 0.5` and a warning is logged

#### Scenario: Value below the recommended minimum is honoured with a warning
- **WHEN** `ttsSettings: {"speed": "0.7"}`
- **THEN** the request includes `audioConfig.speakingRate: 0.7` and a warning is logged

#### Scenario: Value inside the recommended range is sent unchanged
- **WHEN** `ttsSettings: {"speed": "1.2"}`
- **THEN** the request includes `audioConfig.speakingRate: 1.2` and no warning is logged

### Requirement: Stored EXPRESSIVE delivery mode migrated to CREATIVE
A one-off Flyway migration SHALL rewrite `"deliveryMode": "EXPRESSIVE"` to `"deliveryMode": "CREATIVE"` in the `podcasts.tts_settings` JSON column. Podcasts without a `deliveryMode` key, or with any other value, SHALL be left untouched.

#### Scenario: Stored EXPRESSIVE is rewritten
- **WHEN** a podcast has `tts_settings` containing `"deliveryMode":"EXPRESSIVE"` before the migration runs
- **THEN** after migration its `tts_settings` contains `"deliveryMode":"CREATIVE"` and all other keys are unchanged

#### Scenario: Other delivery modes untouched
- **WHEN** a podcast has `"deliveryMode":"STABLE"`
- **THEN** the migration leaves the value unchanged

#### Scenario: Podcasts without ttsSettings untouched
- **WHEN** a podcast has a null `tts_settings`
- **THEN** the migration leaves the row unchanged

