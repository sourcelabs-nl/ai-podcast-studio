## MODIFIED Requirements

### Requirement: TTS provider interface
The system SHALL define a `TtsProvider` interface with a `generate(request: TtsRequest): TtsResult` method, a `scriptGuidelines(style: PodcastStyle): String` method, and a `maxChunkSize: Int` property. The `TtsRequest` SHALL contain the script text, voice configuration (`ttsVoices` map), provider-specific settings (`ttsSettings` map), and language. The `TtsResult` SHALL contain audio chunks and a total character count. Each audio chunk SHALL carry the audio bytes together with the identifier of the voice that produced them, so that the concatenator can level each voice separately. A provider that mixes several speakers into one chunk SHALL tag that chunk as mixed rather than naming one of the speakers.

#### Scenario: Provider generates audio from request
- **WHEN** a `TtsProvider.generate()` is called with a valid `TtsRequest`
- **THEN** the provider returns a `TtsResult` with audio data and character count

#### Scenario: Provider signals concatenation not needed
- **WHEN** a provider returns a single audio file (e.g., ElevenLabs dialogue)
- **THEN** `audioChunks` contains exactly one element

#### Scenario: Provider signals concatenation needed
- **WHEN** a provider returns multiple audio chunks (e.g., OpenAI TTS with a long script)
- **THEN** `audioChunks` contains multiple elements, in script order

#### Scenario: Chunks carry their voice
- **WHEN** a dialogue provider generates chunks for two speakers
- **THEN** each chunk names the voice that produced it, and chunks of different speakers name different voices

#### Scenario: Provider declares script guidelines
- **WHEN** `scriptGuidelines(style)` is called on any `TtsProvider`
- **THEN** the provider returns a string with LLM prompt instructions for formatting scripts

#### Scenario: Provider declares max chunk size
- **WHEN** `maxChunkSize` is accessed on any `TtsProvider`
- **THEN** the provider returns a positive integer representing the maximum characters per API request
