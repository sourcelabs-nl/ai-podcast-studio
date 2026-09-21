## MODIFIED Requirements

### Requirement: Inworld steering instructions with per-chunk re-emission
Inworld steering instructions SHALL be sent only on `inworld-tts-2`, the only model that supports them. A steering instruction is a bracketed free-form English tag that is not one of the documented sound names, for example `[warm and conversational with an easy pace]`. An instruction stays in force until it is changed or cleared with `[reset]`, but only within a single request.

Because the provider splits a turn into multiple requests, the provider SHALL track the active instruction across the chunks of a turn and SHALL prepend it to the head of any subsequent chunk that does not already begin with an instruction, so the direction is not silently lost at a splice point. `[reset]` SHALL clear the active instruction. Sound tags SHALL NOT change the active instruction. Tracking SHALL restart at the beginning of each dialogue turn, so a direction given to one speaker does not carry into another speaker's voice.

Re-emission SHALL be applied only when the model supports steering.

Each speaker's first chunk SHALL be sent without a delivery instruction. `synthesisContext.previousRequests` carries the text of preceding chunks, but on a speaker's first turn that text was spoken in another voice, so an instruction there has no preceding audio of its own to anchor it and the engine over-commits to the cue instead of colouring an established read. After chunking and re-emission, the provider SHALL therefore remove a leading delivery instruction from the first chunk of the monologue script, and from the first chunk of each role's first dialogue turn, via `InworldScriptPostProcessor.stripLeadingInstruction`. A leading sound tag such as `[laugh]` SHALL be kept. Later chunks of that same opening turn, and every later turn by that speaker, SHALL still receive the instruction, because they are anchored by audio in the right voice.

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
- **WHEN** a role's second turn sets `[excited and fast]` and the following turn by another role sets no instruction
- **THEN** the following turn's chunks are not prefixed with `[excited and fast]`

#### Scenario: Script opening is sent unsteered
- **WHEN** a monologue script opens with `[warm and conversational]` and is split into three chunks on `inworld-tts-2`
- **THEN** chunk one is sent with no instruction, while chunks two and three are prefixed with `[warm and conversational]`

#### Scenario: Sound tag on the script opening is kept
- **WHEN** a script opens with `[laugh] Welcome back.`
- **THEN** the first chunk is sent as `[laugh] Welcome back.`

#### Scenario: Every speaker's entrance is sent unsteered
- **WHEN** the host's first turn opens with `[with quiet awe]` and the co-host's first turn opens with `[measured and clear]`
- **THEN** both turns are sent with no instruction

#### Scenario: A speaker's later turn keeps its instruction
- **WHEN** the host has already spoken and a later host turn opens with `[excited and fast]`
- **THEN** that turn is sent with `[excited and fast]` intact
