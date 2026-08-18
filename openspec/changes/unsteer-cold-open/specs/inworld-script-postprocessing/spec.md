## ADDED Requirements

### Requirement: Post-processor strips a delivery instruction from the script opening
The system SHALL provide `InworldScriptPostProcessor.stripLeadingInstruction(text: String): String`, which removes a bracketed delivery instruction occupying the very start of `text`, together with a single following space.

The function SHALL remove the leading tag only when it is a steering instruction as defined by `isSteeringInstruction`. A leading documented sound name such as `[laugh]` SHALL be kept, because a sound is not a character direction. Text with no leading bracketed tag SHALL be returned unchanged, and an instruction that appears anywhere other than the start SHALL be left in place.

This exists because the first synthesis request of a script is the only one sent without `synthesisContext.previousRequests`. With no preceding audio to anchor the read, Inworld treats a delivery instruction as the whole character of the delivery rather than a colouring of an established one, so a cue such as `[with quiet awe]` turns a cold open into a hushed bedtime story.

#### Scenario: Leading delivery instruction removed
- **WHEN** `stripLeadingInstruction("[with quiet awe] Picture someone with a laptop.")` is called
- **THEN** it returns `Picture someone with a laptop.`

#### Scenario: Leading sound tag kept
- **WHEN** `stripLeadingInstruction("[laugh] I can't believe it.")` is called
- **THEN** the text is returned unchanged, including the `[laugh]` tag

#### Scenario: Instruction that is not leading is kept
- **WHEN** `stripLeadingInstruction("Picture this. [brightening] And then it shipped.")` is called
- **THEN** the text is returned unchanged

#### Scenario: Untagged text unchanged
- **WHEN** `stripLeadingInstruction("Welcome to the show.")` is called
- **THEN** the text is returned unchanged

## MODIFIED Requirements

### Requirement: Inworld provider applies post-processing before TTS generation
The `InworldTtsProvider` SHALL apply `InworldScriptPostProcessor.process()` to the script text before passing it to `TextChunker` or the Inworld API, passing `retainSteeringInstructions` according to whether the resolved model supports steering. For monologue styles, the full script SHALL be post-processed. For dialogue styles, each `DialogueTurn.text` SHALL be post-processed individually.

The provider SHALL additionally apply `InworldScriptPostProcessor.stripLeadingInstruction()` to the first chunk of the script's opening, and to that chunk only. The script's opening is the monologue script, or dialogue turn index `0`. This SHALL happen after chunking and after steering re-emission, so the instruction is removed from the one unanchored request while the chunks that follow keep the re-emitted copy.

#### Scenario: Monologue script post-processed before chunking
- **WHEN** a monologue script with markdown artifacts is sent to `InworldTtsProvider`
- **THEN** the provider post-processes the script before chunking and sending to the API

#### Scenario: Dialogue turns post-processed individually
- **WHEN** a dialogue script with unsupported `[emotion]` tags is sent to `InworldTtsProvider` on a model without steering
- **THEN** each turn's text is post-processed before chunking and sending to the API

#### Scenario: Steering retention follows the resolved model
- **WHEN** the resolved model is `inworld-tts-2`
- **THEN** the provider post-processes with `retainSteeringInstructions = true`

#### Scenario: Opening chunk of a monologue is stripped
- **WHEN** a monologue script on `inworld-tts-2` opens with `[with quiet awe] Picture someone with a laptop.` and fits in one chunk
- **THEN** the API receives `Picture someone with a laptop.` with no instruction

#### Scenario: Only the first dialogue turn is stripped
- **WHEN** a dialogue script's first turn opens with `[with quiet awe]` and its second turn opens with `[excited and fast]`
- **THEN** the first turn's request carries no instruction and the second turn's request still carries `[excited and fast]`
