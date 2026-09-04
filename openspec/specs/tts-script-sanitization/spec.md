# tts-script-sanitization Specification

## Purpose
Cleans a finished script on its way to a TTS provider, removing notation the engine would
vocalise rather than act on: em-dashes and en-dashes, which are read aloud as "dash", and IPA
phoneme spans for terms the podcast's pronunciation dictionary does not list, which are read
aloud as a mispronounced word. Sanitization is centralised so that every provider and the
script preview receive exactly the same text, which is what makes a preview representative.
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

### Requirement: Unlisted IPA spans are removed before TTS
The system SHALL remove from every script, before it reaches any TTS provider, each slash-delimited IPA span whose text is not one of the values in the podcast's pronunciation dictionary. Removal SHALL happen in the same central sanitization step as dash handling, so it applies to every provider and to the script preview alike.

An IPA span SHALL be recognised as a slash-delimited run containing no whitespace and no slash, and at least one non-ASCII character. The non-ASCII requirement keeps ordinary prose intact, since an all-ASCII slash pair is far more likely to be `and/or` or `TCP/IP` than a transcription.

The span SHALL be dropped rather than replaced with a guess, because the intended word cannot be recovered from a phoneme string.

The compose prompt already reserves the notation for the listed terms, in the pronunciation guide and again in the composer rules, and a model given a one-entry dictionary still invented `/stɛfan/` for a second speaker whose plain spelling reads correctly. The engine then read the invented transcription out as a mispronounced name, so the rule is enforced here rather than restated in the prompt.

#### Scenario: A listed term's IPA survives
- **WHEN** the dictionary maps `Jarno` to `/jɑrnoː/` and the script contains `/jɑrnoː/`
- **THEN** the provider receives the span unchanged

#### Scenario: An invented IPA span is dropped
- **WHEN** the dictionary holds no entry whose value is `/stɛfan/` and the script contains `And I'm /stɛfan/ ... I mean, Stephan.`
- **THEN** the provider receives `And I'm ... I mean, Stephan.`

#### Scenario: No dictionary means no IPA at all
- **WHEN** the podcast has no pronunciation dictionary and the script contains an IPA span
- **THEN** the span is dropped

#### Scenario: All-ASCII slash pairs are left alone
- **WHEN** a script contains `Use TCP/IP for input/output, and/or a queue, 24/7.`
- **THEN** the provider receives that text unchanged

#### Scenario: A span does not reach across a line break
- **WHEN** a slash appears on one line and the next slash appears on a following line
- **THEN** no text between them is removed
