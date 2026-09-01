# Capability: Inworld Script Post-processing

## Purpose

Sanitises LLM-composed scripts for Inworld TTS: markdown and emoji removed, sound tags normalised, and delivery directions forwarded only where the model understands them and only when they colour rather than flatten the read.

## Requirements

### Requirement: Inworld script post-processor sanitizes LLM output
The system SHALL provide an `InworldScriptPostProcessor` object with a `process(script: String, retainSteeringInstructions: Boolean = false): String` method that sanitizes LLM-generated scripts for Inworld TTS consumption. The post-processor SHALL apply transformations in the following order:
1. Convert double-asterisk emphasis (`**word**`) to single-asterisk emphasis (`*word*`)
2. Strip markdown headers (lines starting with `#`)
3. Strip markdown bullet points (lines starting with `- ` or `* ` — only when followed by a space to preserve `*emphasis*`)
4. Convert markdown links `[text](url)` to just `text`
5. Strip emoji characters
6. Resolve bracketed tags according to the rules below

Bracketed tag handling SHALL be deliberate rather than a side effect of the tag pattern. For each `[...]` tag remaining after markdown links have been resolved:
- A documented Inworld sound name (`sigh`, `laugh`, `breathe`, `cough`, `clear throat`, `yawn`) SHALL be kept and normalised to lowercase with a space, so a legacy `[clear_throat]` is rewritten to `[clear throat]`
- Any other tag whose content is alphabetic (letters, spaces, apostrophes, commas and hyphens), including `[reset]`, is a steering instruction. It SHALL be kept when `retainSteeringInstructions` is true and stripped otherwise, because a model that does not support steering reads the tag aloud
- Any remaining tag (for example a numeric citation marker such as `[1]`) SHALL be stripped

The provider SHALL pass `retainSteeringInstructions = true` only for models that support steering (`inworld-tts-2`).

#### Scenario: Double asterisks converted to single
- **WHEN** the script contains `**important**`
- **THEN** the post-processor converts it to `*important*`

#### Scenario: Single asterisks preserved
- **WHEN** the script contains `*emphasis*`
- **THEN** the post-processor leaves it unchanged

#### Scenario: Markdown headers stripped
- **WHEN** the script contains a line `## Breaking News`
- **THEN** the post-processor removes the entire line

#### Scenario: Markdown bullets stripped
- **WHEN** the script contains a line `- First item`
- **THEN** the post-processor removes the bullet prefix, keeping only `First item`

#### Scenario: Emphasis asterisks at line start not stripped as bullets
- **WHEN** the script contains `*stressed word* in a sentence`
- **THEN** the post-processor preserves the line unchanged (does not treat `*` as a bullet)

#### Scenario: Markdown links converted to plain text
- **WHEN** the script contains `[Anthropic](https://anthropic.com)`
- **THEN** the post-processor converts it to `Anthropic`

#### Scenario: Emojis stripped
- **WHEN** the script contains an emoji
- **THEN** the post-processor removes it

#### Scenario: Supported non-verbal tags preserved
- **WHEN** the script contains `[sigh] I can't believe it`
- **THEN** the post-processor preserves the `[sigh]` tag

#### Scenario: Documented sound name with a space preserved
- **WHEN** the script contains `[clear throat] Right, moving on`
- **THEN** the post-processor preserves the `[clear throat]` tag

#### Scenario: Legacy underscore sound name corrected
- **WHEN** the script contains `[clear_throat]`
- **THEN** the post-processor rewrites it to `[clear throat]`

#### Scenario: Steering instruction retained on a steering-capable model
- **WHEN** `process("[warm and conversational] Welcome.", retainSteeringInstructions = true)` is called
- **THEN** the `[warm and conversational]` tag is preserved

#### Scenario: Steering instruction stripped on a model without steering
- **WHEN** `process("[warm and conversational] Welcome.")` is called
- **THEN** the tag is removed, producing `Welcome.`

#### Scenario: Single-word instruction handled the same as a multi-word one
- **WHEN** the script contains `[excited]` and `[say excitedly]`
- **THEN** both are retained when steering is enabled and both are stripped when it is not

#### Scenario: Reset tag follows the steering rule
- **WHEN** the script contains `[reset]`
- **THEN** it is retained when steering is enabled and stripped when it is not

#### Scenario: Non-alphabetic tags always stripped
- **WHEN** the script contains `[1]`
- **THEN** the tag is removed regardless of whether steering is enabled

#### Scenario: Multiple transformations applied together
- **WHEN** the script contains `## Intro\n**Welcome** to the show! [excitedly] Let's begin.` and steering is disabled
- **THEN** the post-processor produces `*Welcome* to the show! Let's begin.`

### Requirement: Inworld provider applies post-processing before TTS generation
The `InworldTtsProvider` SHALL apply `InworldScriptPostProcessor.process()` to the script text before passing it to `TextChunker` or the Inworld API, passing `retainSteeringInstructions` according to whether the resolved model supports steering. For monologue styles, the full script SHALL be post-processed. For dialogue styles, each `DialogueTurn.text` SHALL be post-processed individually.

#### Scenario: Monologue script post-processed before chunking
- **WHEN** a monologue script with markdown artifacts is sent to `InworldTtsProvider`
- **THEN** the provider post-processes the script before chunking and sending to the API

#### Scenario: Dialogue turns post-processed individually
- **WHEN** a dialogue script with unsupported `[emotion]` tags is sent to `InworldTtsProvider` on a model without steering
- **THEN** each turn's text is post-processed before chunking and sending to the API

#### Scenario: Steering retention follows the resolved model
- **WHEN** the resolved model is `inworld-tts-2`
- **THEN** the provider post-processes with `retainSteeringInstructions = true`

### Requirement: Delivery directions that flatten or distort a read are dropped
`InworldScriptPostProcessor` SHALL strip a steering instruction whose content names a delivery that removes expression or reduces audibility, instead of forwarding it to the engine. The words covered SHALL include `deadpan`, `monotone`, `monotonous`, `flat`, `flatly`, `robotic`, `mechanical`, `emotionless`, `expressionless`, `lifeless`, `dull`, `bored`, `disinterested`, `uninterested`, `whisper`, `whispering`, `whispered`, `muttering`, `mumbling`, `shouting`, `shouted`, `screaming` and `yelling`.

The match SHALL be on whole words within the instruction, so a phrase such as `[in a deadpan tone]` or `[flat and bored]` is caught and not only the bare word. Each drop SHALL be logged so the suppression is visible.

Stripping is the safe direction: the turn falls back to neutral delivery, which is never wrong, whereas a forwarded instruction is obeyed literally. `[deadpan]` on an expert turn a quarter of the way into episode 194 produced roughly 25 seconds of flat, expressionless delivery that a listener noticed. A false positive costs a little colour; a false negative costs part of an episode.

This rule SHALL apply only where instructions would otherwise be retained — for a model without steering support every instruction is stripped already.

#### Scenario: A bare flattening cue is dropped
- **WHEN** a turn opens with `[deadpan]` and `retainSteeringInstructions` is true
- **THEN** the tag is removed and the turn's words are left unchanged

#### Scenario: A flattening word inside a phrase is dropped
- **WHEN** a turn opens with `[in a deadpan tone]`
- **THEN** the whole tag is removed

#### Scenario: An expressive cue is still forwarded
- **WHEN** a turn opens with `[warm and conversational with an easy pace]`
- **THEN** the tag is kept, because it colours the read rather than flattening it

#### Scenario: Reset is still forwarded
- **WHEN** a turn contains `[reset]`
- **THEN** the tag is kept, since returning to neutral delivery is not a flattening instruction

#### Scenario: Sound tags are unaffected
- **WHEN** a turn contains `[sigh]` or `[laugh]`
- **THEN** the tag is kept as a sound, regardless of the suppression rule

#### Scenario: A dropped cue cannot be re-emitted onto later chunks
- **WHEN** a suppressed instruction is stripped and the turn is then split into several chunks
- **THEN** no chunk carries the instruction, because suppression happens before chunking and steering re-emission
