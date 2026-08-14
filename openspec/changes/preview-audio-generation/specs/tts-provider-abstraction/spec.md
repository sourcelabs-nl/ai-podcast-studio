## MODIFIED Requirements

### Requirement: TTS provider interface
The system SHALL define a `TtsProvider` interface with a `generate(request: TtsRequest): TtsResult` method, a `scriptGuidelines(style: PodcastStyle): String` method, and a `maxChunkSize: Int` property. The `TtsRequest` SHALL contain the script text, voice configuration (`ttsVoices` map), provider-specific settings (`ttsSettings` map), and language. The `TtsResult` SHALL contain audio chunks (list of byte arrays), total character count, and a flag indicating whether FFmpeg concatenation is needed.

The `TtsRequest` SHALL additionally carry an optional `TtsProgressListener`. Only the provider knows how many chunks a script became and when each finished, so every provider SHALL invoke the listener as each chunk completes, reporting the number completed and the total. A provider that synthesises chunks concurrently SHALL report completions rather than chunk indices, so the reported count rises monotonically. When no listener is supplied, providers SHALL behave exactly as before.

#### Scenario: Provider generates audio from request
- **WHEN** a `TtsProvider.generate()` is called with a valid `TtsRequest`
- **THEN** the provider returns a `TtsResult` with audio data and character count

#### Scenario: Provider signals concatenation not needed
- **WHEN** a provider returns a single audio file (e.g., ElevenLabs dialogue)
- **THEN** `TtsResult.requiresConcatenation` is `false` and `audioChunks` contains exactly one element

#### Scenario: Provider signals concatenation needed
- **WHEN** a provider returns multiple audio chunks (e.g., OpenAI TTS with a long script)
- **THEN** `TtsResult.requiresConcatenation` is `true` and `audioChunks` contains multiple elements

#### Scenario: Provider declares script guidelines
- **WHEN** `scriptGuidelines(style)` is called on any `TtsProvider`
- **THEN** the provider returns a string with LLM prompt instructions for formatting scripts

#### Scenario: Provider declares max chunk size
- **WHEN** `maxChunkSize` is accessed on any `TtsProvider`
- **THEN** the provider returns a positive integer representing the maximum characters per API request

#### Scenario: Provider reports chunk progress
- **WHEN** a `TtsRequest` carries a progress listener and the provider completes a chunk
- **THEN** the listener is invoked with the number of chunks completed and the total

#### Scenario: No progress listener supplied
- **WHEN** a `TtsRequest` carries no progress listener
- **THEN** generation proceeds unchanged and nothing is reported
