# Capability: Inworld TTS

## Purpose

Inworld AI TTS provider integration including model selection, delivery mode, expressiveness, and voice discovery.
## Requirements
### Requirement: Inworld AI TTS provider
The system SHALL provide an `InworldTtsProvider` implementing `TtsProvider` that generates audio via the Inworld AI TTS API. The provider SHALL send text to `POST https://api.inworld.ai/tts/v1/voice` with the configured `voiceId` and `modelId`. The response SHALL contain base64-encoded audio in `audioContent` and character usage in `usage.processedCharactersCount`. The provider SHALL decode the base64 audio into byte arrays. The default model SHALL be `inworld-tts-1.5-max`, overridable via `ttsSettings["model"]`. Supported models include `inworld-tts-1.5-max`, `inworld-tts-1.5-mini`, and `inworld-tts-2`. The audio format SHALL be MP3 at 48000 Hz sample rate and 128000 bps bit rate. The provider SHALL use `TextChunker` with its `maxChunkSize` of 2000 to split long scripts. The result SHALL have `requiresConcatenation = true` when multiple chunks are generated. The provider SHALL generate all chunks concurrently using Kotlin coroutines (`async`/`awaitAll` on `Dispatchers.IO`). Chunk ordering in the result SHALL match the original script order regardless of completion order.

The provider SHALL accept an optional `ttsSettings["deliveryMode"]` value (case-insensitive, blank treated as unset) that maps to Inworld's TTS-2 `deliveryMode` enum with values `STABLE`, `BALANCED`, or `EXPRESSIVE`. When `deliveryMode` is set, the provider SHALL forward it as the `deliveryMode` field in the synthesize request body and SHALL NOT send `temperature` (the Inworld API treats them as mutually exclusive on TTS-2). When `deliveryMode` is unset, the provider SHALL fall back to `temperature` behavior: explicit `ttsSettings["temperature"]` if present, otherwise the default `0.8`.

The provider SHALL pass optional knobs (speed, temperature, deliveryMode) to `InworldApiClient.synthesizeSpeech` as a single `InworldSynthesisOptions` data class rather than as separate positional arguments.

#### Scenario: Single chunk generates audio
- **WHEN** a script of 1500 characters is sent to the Inworld provider
- **THEN** the provider makes one API call and returns a `TtsResult` with one audio chunk and `requiresConcatenation = false`

#### Scenario: Long script chunked and generated in parallel
- **WHEN** a script of 5000 characters is sent to the Inworld provider
- **THEN** the provider splits it into 3+ chunks via `TextChunker`, generates all chunks concurrently via coroutines, and returns a `TtsResult` with audio chunks in script order and `requiresConcatenation = true`

#### Scenario: Model override via ttsSettings
- **WHEN** a podcast has `ttsSettings: {"model": "inworld-tts-1.5-mini"}`
- **THEN** the Inworld API is called with `modelId: "inworld-tts-1.5-mini"`

#### Scenario: TTS-2 model override via ttsSettings
- **WHEN** a podcast has `ttsSettings: {"model": "inworld-tts-2"}`
- **THEN** the Inworld API is called with `modelId: "inworld-tts-2"`

#### Scenario: Default model used when not specified
- **WHEN** a podcast has no `"model"` key in `ttsSettings`
- **THEN** the Inworld API is called with `modelId: "inworld-tts-1.5-max"`

#### Scenario: Speed setting applied via ttsSettings
- **WHEN** a podcast has `ttsSettings: {"speed": "1.2"}`
- **THEN** the Inworld API request includes `audioConfig.speakingRate: 1.2` (valid range: 0.5-1.5, default: 1.0)

#### Scenario: Temperature setting applied via ttsSettings
- **WHEN** a podcast has `ttsSettings: {"temperature": "0.8"}` and no `"deliveryMode"` key
- **THEN** the Inworld API request includes `temperature: 0.8` as a top-level field

#### Scenario: Default temperature of 0.8 when not configured
- **WHEN** a podcast has no `"temperature"` and no `"deliveryMode"` key in `ttsSettings`
- **THEN** the Inworld API request includes `temperature: 0.8`

#### Scenario: deliveryMode applied via ttsSettings
- **WHEN** a podcast has `ttsSettings: {"model": "inworld-tts-2", "deliveryMode": "EXPRESSIVE"}`
- **THEN** the Inworld API request body includes `"deliveryMode": "EXPRESSIVE"` and does NOT include the `temperature` field

#### Scenario: deliveryMode is uppercase-normalized
- **WHEN** a podcast has `ttsSettings: {"deliveryMode": "expressive"}`
- **THEN** the Inworld API request body includes `"deliveryMode": "EXPRESSIVE"`

#### Scenario: Blank deliveryMode is treated as unset
- **WHEN** a podcast has `ttsSettings: {"deliveryMode": ""}`
- **THEN** the Inworld API request omits `deliveryMode` and applies the default temperature `0.8`

#### Scenario: deliveryMode suppresses both default and explicit temperature
- **WHEN** a podcast has `ttsSettings: {"deliveryMode": "STABLE", "temperature": "1.1"}`
- **THEN** the Inworld API request body includes `"deliveryMode": "STABLE"` and does NOT include `temperature`

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
The system SHALL handle Inworld API errors with clear error messages. HTTP 401 SHALL indicate invalid or expired credentials. HTTP 429 SHALL be retried with exponential backoff (up to 3 attempts with delays of 1s, 2s, 4s). If retries are exhausted, the system SHALL throw an `InworldRateLimitException`. Other error status codes SHALL include the HTTP status and response body in the error message.

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

#### Scenario: Generic API error
- **WHEN** the Inworld API returns HTTP 500
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
- Non-verbal tags: `[sigh]`, `[laugh]`, `[breathe]`, `[cough]`, `[clear_throat]`, `[yawn]`
- Emphasis: `*word*` (single asterisks) for stressed words
- Pacing: ellipsis (`...`) for trailing pauses, exclamation marks for excitement
- IPA phonemes: `/phoneme/` for precise pronunciation of proper nouns

The guidelines SHALL additionally include:
- Text normalization: write all numbers, dates, currencies, and symbols in fully spoken form
- Anti-markdown: never use markdown formatting; never use `**double asterisks**` as the TTS engine reads asterisk characters aloud
- Contractions: use natural contractions throughout for spoken naturalness
- Punctuation: always end sentences with proper punctuation for correct pacing

For `CASUAL` and `DIALOGUE` styles, guidelines SHALL additionally encourage natural filler words (`uh`, `um`, `well`, `you know`). For `EXECUTIVE_SUMMARY` and `NEWS_BRIEFING` styles, guidelines SHALL instruct to avoid filler words and minimize non-verbal tags.

When `pronunciations` is non-empty, the guidelines SHALL append a "Pronunciation Guide" section listing each term and its IPA phoneme. The guidelines SHALL instruct the LLM to REPLACE the word with its IPA phoneme notation on the first occurrence only (not write both the word and the phoneme), and to ONLY use IPA for the listed terms (never invent IPA for unlisted words). On subsequent occurrences, the word SHALL be written normally. When `pronunciations` is empty, no pronunciation section SHALL be appended.

#### Scenario: Casual style guidelines include filler words
- **WHEN** `scriptGuidelines(PodcastStyle.CASUAL, emptyMap())` is called
- **THEN** the returned text includes instructions to use filler words naturally

#### Scenario: Executive summary guidelines suppress filler words
- **WHEN** `scriptGuidelines(PodcastStyle.EXECUTIVE_SUMMARY, emptyMap())` is called
- **THEN** the returned text instructs to avoid filler words and minimize non-verbal tags

#### Scenario: All styles include core markup and formatting instructions
- **WHEN** `scriptGuidelines()` is called for any style
- **THEN** the returned text includes instructions for non-verbal tags, emphasis, pacing, IPA phonemes, text normalization, anti-markdown, contractions, and punctuation

#### Scenario: Guidelines warn against double asterisks
- **WHEN** `scriptGuidelines()` is called for any style
- **THEN** the returned text explicitly warns that `**double asterisks**` will cause the TTS engine to read asterisk characters aloud

#### Scenario: Guidelines include pronunciation dictionary
- **WHEN** `scriptGuidelines(PodcastStyle.CASUAL, mapOf("Anthropic" to "/ænˈθɹɒpɪk/", "Jarno" to "/jɑrnoː/"))` is called
- **THEN** the returned text includes a "Pronunciation Guide" section with both entries and instructs the LLM to REPLACE the word with its IPA phoneme on first occurrence only, and to ONLY use IPA for listed terms

#### Scenario: Guidelines omit pronunciation section when empty
- **WHEN** `scriptGuidelines(PodcastStyle.CASUAL, emptyMap())` is called
- **THEN** the returned text does not include a "Pronunciation Guide" section

### Requirement: Inworld TTS max chunk size
The `InworldTtsProvider` SHALL declare `maxChunkSize = 2000` matching the Inworld API's 2000 character limit per request.

#### Scenario: Max chunk size is 2000
- **WHEN** the Inworld provider's `maxChunkSize` is queried
- **THEN** it returns 2000

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

