# Capability: TTS Provider Abstraction

## Purpose

A provider-agnostic TTS layer: a `TtsProvider` interface and a `TtsProviderFactory` that resolves the right implementation (OpenAI, ElevenLabs, Inworld; monologue vs dialogue/interview) from the podcast's settings, plus the parsing of tagged dialogue scripts into speaker turns.
## Requirements
### Requirement: TTS provider interface
The system SHALL define a `TtsProvider` interface with a `generate(request: TtsRequest): TtsResult` method, a `scriptGuidelines(style: PodcastStyle): String` method, and a `maxChunkSize: Int` property. The `TtsRequest` SHALL contain the script text, voice configuration (`ttsVoices` map), provider-specific settings (`ttsSettings` map), and language. The `TtsResult` SHALL contain audio chunks (list of byte arrays), total character count, and a flag indicating whether FFmpeg concatenation is needed.

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

### Requirement: TTS provider factory
The system SHALL provide a `TtsProviderFactory` that resolves the appropriate `TtsProvider` implementation based on `podcast.ttsProvider` and `podcast.style`. The factory SHALL be a Spring component that receives all provider implementations via constructor injection.

#### Scenario: OpenAI provider resolved
- **WHEN** a podcast has `ttsProvider` set to `"openai"`
- **THEN** the factory returns the `OpenAiTtsProvider`

#### Scenario: ElevenLabs monologue provider resolved
- **WHEN** a podcast has `ttsProvider` set to `"elevenlabs"` and `style` is not `"dialogue"` or `"interview"`
- **THEN** the factory returns the `ElevenLabsTtsProvider`

#### Scenario: ElevenLabs dialogue provider resolved
- **WHEN** a podcast has `ttsProvider` set to `"elevenlabs"` and `style` is `"dialogue"` or `"interview"`
- **THEN** the factory returns the `ElevenLabsDialogueTtsProvider`

#### Scenario: Inworld monologue provider resolved
- **WHEN** a podcast has `ttsProvider` set to `"inworld"` and `style` is not `"dialogue"` or `"interview"`
- **THEN** the factory returns the `InworldTtsProvider`

#### Scenario: Inworld dialogue provider resolved
- **WHEN** a podcast has `ttsProvider` set to `"inworld"` and `style` is `"dialogue"` or `"interview"`
- **THEN** the factory returns the `InworldTtsProvider` (same provider, handles per-turn generation internally by parsing dialogue turns and generating each with the appropriate voice)

#### Scenario: Unknown provider rejected
- **WHEN** a podcast has `ttsProvider` set to an unsupported value
- **THEN** the factory throws an `IllegalArgumentException` with a message listing supported providers

### Requirement: Tolerant dialogue script parsing
The system SHALL parse tagged dialogue scripts into ordered speaker turns where each turn's role is taken from its opening tag (`<role>`), and the turn's text runs until the next tag token (opening or closing) or the end of the script. The parser SHALL NOT require the closing tag to match the opening tag. This makes parsing robust to the malformed tags an LLM occasionally emits (a turn closed with a different role's closing tag, or a closing tag omitted before the next opening tag) so that spoken turns are never silently dropped. Text that appears before any opening tag, or after a turn ends and before the next opening tag, SHALL be ignored with a warning.

#### Scenario: Closing tag does not match opening tag
- **WHEN** the script contains `<interviewer>What happened?</interviewer><expert>A lot happened.</interviewer>`
- **THEN** two turns are returned: `(interviewer, "What happened?")` and `(expert, "A lot happened.")`

#### Scenario: Closing tag missing before next opening tag
- **WHEN** the script contains `<interviewer>First question<expert>The answer.</expert>`
- **THEN** two turns are returned: `(interviewer, "First question")` and `(expert, "The answer.")`

#### Scenario: Consecutive turns of the same speaker
- **WHEN** the script contains `<interviewer>One.</interviewer><interviewer>Two.</interviewer>`
- **THEN** two separate `interviewer` turns are returned in order

#### Scenario: Final turn without a closing tag
- **WHEN** the script ends with an opened turn that is never closed, e.g. `<expert>Unterminated answer.`
- **THEN** the final turn is recovered as `(expert, "Unterminated answer.")`

#### Scenario: Text outside speaker tags ignored
- **WHEN** the script contains text before the first opening tag or after a turn closes and before the next opening tag
- **THEN** that text is ignored and a warning is logged

