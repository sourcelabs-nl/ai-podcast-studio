## MODIFIED Requirements

### Requirement: Inworld steering instructions with per-chunk re-emission
Inworld steering instructions SHALL be sent only on `inworld-tts-2`, the only model that supports them. A steering instruction is a bracketed free-form English tag that is not one of the documented sound names, for example `[warm and conversational with an easy pace]`. An instruction stays in force until it is changed or cleared with `[reset]`, but only within a single request.

Because the provider splits a turn into multiple requests, the provider SHALL track the active instruction across the chunks of a turn and SHALL prepend it to the head of any subsequent chunk that does not already begin with an instruction, so the direction is not silently lost at a splice point. `[reset]` SHALL clear the active instruction. Sound tags SHALL NOT change the active instruction. Tracking SHALL restart at the beginning of each dialogue turn, so a direction given to one speaker does not carry into another speaker's voice.

Re-emission SHALL be applied only when the model supports steering.

The first chunk of a script SHALL be sent without a delivery instruction. It is the only request sent with no `synthesisContext.previousRequests`, so an instruction there has no preceding audio to anchor it and the engine over-commits to the cue instead of colouring an established read. After chunking and re-emission, the provider SHALL therefore remove a leading delivery instruction from the first chunk of the monologue script or of dialogue turn index `0`, via `InworldScriptPostProcessor.stripLeadingInstruction`. A leading sound tag such as `[laugh]` SHALL be kept. Later chunks of that same opening turn SHALL still receive the re-emitted instruction, because they are anchored by the audio before them.

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
- **WHEN** a middle turn sets `[excited and fast]` and the following turn sets no instruction
- **THEN** the following turn's chunks are not prefixed with `[excited and fast]`

#### Scenario: Script opening is sent unsteered
- **WHEN** a monologue script opens with `[warm and conversational]` and is split into three chunks on `inworld-tts-2`
- **THEN** chunk one is sent with no instruction, while chunks two and three are prefixed with `[warm and conversational]`

#### Scenario: Sound tag on the script opening is kept
- **WHEN** a script opens with `[laugh] Welcome back.`
- **THEN** the first chunk is sent as `[laugh] Welcome back.`

### Requirement: Inworld TTS script guidelines
The `InworldTtsProvider` SHALL return style-aware script guidelines via `scriptGuidelines(style, pronunciations)`. The guidelines SHALL instruct the LLM to use Inworld-specific markup:
- Non-verbal tags: `[sigh]`, `[laugh]`, `[breathe]`, `[cough]`, `[clear throat]`, `[yawn]` — spelled exactly as Inworld documents them, with a space rather than an underscore in `[clear throat]`
- Emphasis: `*word*` (single asterisks) for stressed words, or CAPS for a whole word or a single syllable (`AbsoLUTEly`)
- Pacing: ellipsis (`...`) for trailing pauses, exclamation marks for excitement
- Pauses: SSML break tags such as `<break time="1s" />` for a deliberate beat between segments, at most 20 per request and at most 10 seconds each, and not immediately before a paragraph break where the pause already exists
- Steering: at most one short free-form English delivery instruction in square brackets (for example `[warm and conversational with an easy pace]`) at the start of a speaker turn or segment, with `[reset]` to return to neutral delivery
- Acronyms: expand on first use, then use the short form — spoken as a word when pronounceable and spelled out letter by letter when not, because Inworld's normalization does not cover domain acronyms
- IPA phonemes: `/phoneme/` for precise pronunciation of proper nouns

The guidelines SHALL instruct the LLM never to put a delivery direction on the script's very first turn, explaining that the opening is synthesized with no preceding audio to anchor it, so the engine over-commits to the cue and a mood such as `[with quiet awe]` makes the cold open sound like a hushed bedtime story. The opening words SHALL be left to carry the tone themselves.

The guidelines SHALL additionally include:
- Text normalization: write all numbers, dates, currencies, and symbols in fully spoken form
- Anti-markdown: never use markdown formatting; never use `**double asterisks**` as the TTS engine reads asterisk characters aloud
- Contractions: use natural contractions throughout for spoken naturalness
- Punctuation: always end sentences with proper punctuation for correct pacing

The steering guidance SHALL be emitted for every style and model. The post-processor is responsible for stripping steering instructions on models that do not support them, so the guidelines do not need a model parameter.

For `CASUAL`, `DIALOGUE` and `INTERVIEW` styles, guidelines SHALL additionally encourage natural filler words (`uh`, `um`, `well`, `you know`), because disfluencies are what make synthesised conversational speech sound human. For `EXECUTIVE_SUMMARY` and `NEWS_BRIEFING` styles, guidelines SHALL instruct to avoid filler words and minimize non-verbal tags.

When `pronunciations` is non-empty, the guidelines SHALL append a "Pronunciation Guide" section listing each term and its IPA phoneme. The guidelines SHALL instruct the LLM to REPLACE the word with its IPA phoneme notation on every occurrence (not write both the word and the phoneme), and to ONLY use IPA for the listed terms (never invent IPA for unlisted words). When `pronunciations` is empty, no pronunciation section SHALL be appended.

#### Scenario: Casual style guidelines include filler words
- **WHEN** `scriptGuidelines(PodcastStyle.CASUAL, emptyMap())` is called
- **THEN** the returned text includes instructions to use filler words naturally

#### Scenario: Interview style guidelines include filler words
- **WHEN** `scriptGuidelines(PodcastStyle.INTERVIEW, emptyMap())` is called
- **THEN** the returned text includes instructions to use filler words naturally and does not instruct to avoid them

#### Scenario: Executive summary guidelines suppress filler words
- **WHEN** `scriptGuidelines(PodcastStyle.EXECUTIVE_SUMMARY, emptyMap())` is called
- **THEN** the returned text instructs to avoid filler words and minimize non-verbal tags

#### Scenario: Every style forbids a delivery direction on the first turn
- **WHEN** `scriptGuidelines(style, emptyMap())` is called for any `PodcastStyle`
- **THEN** the returned text instructs never to put a delivery direction on the script's very first turn
