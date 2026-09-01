# Capability: TTS Script Profile

## Purpose

The per-provider script guidelines handed to the composer, telling it which expressiveness markup the TTS engine understands and what it may ask that engine to do.

## Requirements

### Requirement: TTS provider declares script guidelines
Each `TtsProvider` implementation SHALL provide a `scriptGuidelines(style: PodcastStyle, pronunciations: Map<String, String>): String` method that returns LLM prompt instructions specific to that provider's capabilities. The `pronunciations` parameter SHALL default to an empty map so existing callers without pronunciations continue to work. The guidelines describe what markup, emotion tags, or formatting the TTS engine supports so the LLM can generate optimized scripts. For the Inworld provider, guidelines SHALL additionally include text normalization rules, anti-markdown warnings, contractions guidance, punctuation rules, and a pronunciation guide section when pronunciations are provided.

#### Scenario: OpenAI returns plain text guidelines
- **WHEN** `scriptGuidelines()` is called on `OpenAiTtsProvider`
- **THEN** it returns instructions to write clean natural speech without special markup, emotion tags, or non-verbal cues

#### Scenario: ElevenLabs returns emotion cue guidelines
- **WHEN** `scriptGuidelines()` is called on `ElevenLabsTtsProvider`
- **THEN** it returns instructions allowing emotion cues in square brackets (e.g., `[cheerfully]`, `[seriously]`)

#### Scenario: ElevenLabs dialogue provider shares single-speaker guidelines
- **WHEN** `scriptGuidelines()` is called on `ElevenLabsDialogueTtsProvider`
- **THEN** it returns the same emotion cue guidelines as `ElevenLabsTtsProvider`

#### Scenario: Inworld returns comprehensive formatting guidelines
- **WHEN** `scriptGuidelines()` is called on `InworldTtsProvider`
- **THEN** it returns instructions covering non-verbal tags, emphasis, pacing, IPA phonemes, text normalization, anti-markdown rules, contractions, and punctuation

#### Scenario: Inworld includes pronunciation guide when pronunciations provided
- **WHEN** `scriptGuidelines(style, mapOf("Anthropic" to "/ænˈθɹɒpɪk/"))` is called on `InworldTtsProvider`
- **THEN** it returns guidelines that include a "Pronunciation Guide" section with the provided entries

### Requirement: TTS provider declares max chunk size
Each `TtsProvider` implementation SHALL declare a `maxChunkSize: Int` property indicating the maximum number of characters per TTS API request.

#### Scenario: OpenAI max chunk size
- **WHEN** `OpenAiTtsProvider.maxChunkSize` is queried
- **THEN** it returns 4096

#### Scenario: ElevenLabs max chunk size
- **WHEN** `ElevenLabsTtsProvider.maxChunkSize` is queried
- **THEN** it returns 5000

#### Scenario: ElevenLabs dialogue max chunk size
- **WHEN** `ElevenLabsDialogueTtsProvider.maxChunkSize` is queried
- **THEN** it returns 5000

### Requirement: Composers inject TTS script guidelines into prompts
All three composers (`BriefingComposer`, `DialogueComposer`, `InterviewComposer`) SHALL accept a `ttsScriptGuidelines: String` parameter in their `compose()` methods. The guidelines SHALL be injected into the LLM prompt as an additional block after the existing requirements section. When the guidelines string is empty, no additional block SHALL be added.

#### Scenario: Briefing composer includes Inworld guidelines
- **WHEN** `BriefingComposer.compose()` is called with Inworld script guidelines
- **THEN** the LLM prompt includes the Inworld emotion tags and emphasis instructions

#### Scenario: Dialogue composer includes provider guidelines
- **WHEN** `DialogueComposer.compose()` is called with ElevenLabs script guidelines
- **THEN** the LLM prompt includes the ElevenLabs emotion cue instructions

#### Scenario: Empty guidelines add nothing to prompt
- **WHEN** a composer is called with an empty string for `ttsScriptGuidelines`
- **THEN** the LLM prompt has no additional TTS-specific block

### Requirement: LlmPipeline resolves and passes TTS script guidelines
The `LlmPipeline` SHALL resolve the `TtsProvider` via `TtsProviderFactory` before calling a composer. It SHALL call `scriptGuidelines(podcast.style, podcast.pronunciations ?: emptyMap())` on the resolved provider and pass the result to the composer. The `LlmPipeline` SHALL gain a constructor dependency on `TtsProviderFactory`.

#### Scenario: Pipeline passes guidelines with pronunciations to briefing composer
- **WHEN** the pipeline runs for a podcast with `ttsProvider: INWORLD`, `style: CASUAL`, and `pronunciations: {"Anthropic": "/ænˈθɹɒpɪk/"}`
- **THEN** it resolves the Inworld provider, calls `scriptGuidelines(CASUAL, {"Anthropic": "/ænˈθɹɒpɪk/"})`, and passes the result to `BriefingComposer.compose()`

#### Scenario: Pipeline passes empty pronunciations when none configured
- **WHEN** the pipeline runs for a podcast with `ttsProvider: INWORLD`, `style: CASUAL`, and `pronunciations: null`
- **THEN** it calls `scriptGuidelines(CASUAL, emptyMap())` and passes the result to the composer

### Requirement: Delivery directions may colour a read, never flatten it
The Inworld script guidelines SHALL tell the composer that a delivery direction may adjust warmth, energy or pace, and SHALL NOT ask for a delivery that removes expression or makes a turn harder to hear — naming `deadpan`, `monotone`, `robotic` and `whispering` as examples to avoid.

The engine obeys such a direction literally, so the instruction is the first line of defence and the post-processor's suppression rule is the backstop. `[deadpan]` reached episode 194 this way and flattened an expert turn for roughly 25 seconds.

#### Scenario: Guidelines constrain delivery directions
- **WHEN** the Inworld guidelines are built for any podcast style
- **THEN** they state that a delivery direction may adjust warmth, energy or pace but must not remove expression or reduce audibility

#### Scenario: Flattening examples are named
- **WHEN** the guidelines describe delivery directions
- **THEN** they name at least `deadpan` and `monotone` as directions not to use
