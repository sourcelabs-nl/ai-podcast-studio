# tts-script-sanitization Specification

## Purpose
TBD - created by archiving change strip-dashes-before-tts. Update Purpose after archive.
## Requirements
### Requirement: Em-dash and en-dash sanitization before TTS
The system SHALL sanitize every script before it is sent to any TTS provider by replacing em-dash characters (`—`, U+2014) and en-dash characters (`–`, U+2013) with a comma followed by a space (`, `). Sanitization SHALL be applied centrally in `TtsPipeline.callProvider` so it covers every TTS provider (Inworld, ElevenLabs, ElevenLabs Dialogue, OpenAI) without provider-specific changes.

#### Scenario: Em-dash inside a sentence
- **WHEN** a script contains the substring `foo — bar`
- **THEN** the TTS provider receives `foo , bar`

#### Scenario: En-dash inside a sentence
- **WHEN** a script contains the substring `foo – bar`
- **THEN** the TTS provider receives `foo , bar`

#### Scenario: Multiple dashes across the script
- **WHEN** a script contains several em-dashes and en-dashes scattered across paragraphs
- **THEN** every occurrence is replaced; no em-dash or en-dash character remains in the text passed to the provider

#### Scenario: Dash directly followed by sentence terminator collapsed
- **WHEN** a script contains `foo —. Next sentence`
- **THEN** the TTS provider receives `foo. Next sentence` (the inserted `", "` is collapsed against the following terminator so the script does not contain `,. ` artifacts)

#### Scenario: Script without dashes is unchanged
- **WHEN** a script contains no em-dash or en-dash characters
- **THEN** the text passed to the TTS provider is byte-identical to the input

#### Scenario: Persisted script retains original characters
- **WHEN** an episode is generated from a script that contained em-dashes
- **THEN** the `scriptText` saved on the episode row is the original LLM output (em-dashes preserved), and only the text handed to the TTS provider is sanitized

