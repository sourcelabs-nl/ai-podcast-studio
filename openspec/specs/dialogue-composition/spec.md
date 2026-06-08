# Capability: Dialogue Composition

## Purpose

Multi-speaker dialogue composition for podcast scripts, producing natural conversations with XML speaker tags for TTS processing.
## Requirements
### Requirement: DialogueComposer generates speaker-tagged scripts
The system SHALL provide a `DialogueComposer` component that generates multi-speaker dialogue scripts using XML-style speaker tags. The composer SHALL use the `compose` model (resolved via `ModelResolver`). The output format SHALL use tags matching the keys in the podcast's `ttsVoices` map (e.g., `<host>`, `<cohost>`). The composer SHALL NOT strip any tags from the output — the tags are required for downstream processing.

#### Scenario: Dialogue script generated with two speakers
- **WHEN** the `DialogueComposer` composes a script for a podcast with `ttsVoices: {"host": "id1", "cohost": "id2"}`
- **THEN** the output contains alternating `<host>` and `<cohost>` tags with natural conversation

#### Scenario: Composer uses compose model
- **WHEN** the `DialogueComposer` is invoked
- **THEN** it resolves and uses the `compose` stage model via `ModelResolver`

#### Scenario: Tags are not stripped from output
- **WHEN** the LLM produces a dialogue script with `<host>` and `<cohost>` tags
- **THEN** the tags are preserved in the returned script (no `stripSectionHeaders` applied)

### Requirement: DialogueComposer prompt engineering
The DialogueComposer prompt SHALL instruct the model on script structure and engagement, including conversational coherence.

**No empty setup turns:** The prompt SHALL forbid contentless setup turns. When a speaker announces or teases a specific point (a caveat, flag, question, fact, or statistic), that same speaker SHALL state its substance in the same turn. A handoff to the other speaker is permitted only when that speaker adds genuinely new information, not when they complete a point the first speaker merely gestured at.

#### Scenario: No contentless setup turns
- **WHEN** the dialogue prompt is built
- **THEN** it instructs the model that a speaker who teases a specific point must state that point in the same turn rather than handing its substance to the other speaker

### Requirement: Composer selection based on podcast style
The system SHALL select the appropriate composer based on the podcast's `style` field. The `"dialogue"` style SHALL use `DialogueComposer`. The `"interview"` style SHALL use `InterviewComposer`. All other styles SHALL use `BriefingComposer`. The selection SHALL happen in the pipeline orchestration layer (`LlmPipeline`).

#### Scenario: Dialogue style uses DialogueComposer
- **WHEN** a podcast has `style: "dialogue"`
- **THEN** the pipeline uses `DialogueComposer` for script generation

#### Scenario: News-briefing style uses BriefingComposer
- **WHEN** a podcast has `style: "news-briefing"`
- **THEN** the pipeline uses `BriefingComposer` for script generation

#### Scenario: Casual style uses BriefingComposer
- **WHEN** a podcast has `style: "casual"`
- **THEN** the pipeline uses `BriefingComposer` for script generation

### Requirement: Dialogue prompts use variety rotation

The dialogue composer prompt SHALL be parameterized by the `PromptVarietyPicker` selection for the current `(podcastId, episodeDate)`. Opening style, transition vocabulary, and sign-off shape MUST come from the picker, not be hard-coded constants.

#### Scenario: Different dates produce different prompt scaffolding

- **WHEN** the dialogue composer builds prompts for the same podcast on two different dates with the same article set
- **THEN** the prompt strings differ in the opening-style and sign-off-shape sections

### Requirement: Dialogue prompts contain no verbatim example phrases

The dialogue composer prompt SHALL NOT include verbatim sample sentences that the LLM is prone to copy into generated scripts. Structural beats may be described abstractly.

#### Scenario: No banned phrases present

- **WHEN** the dialogue composer prompt is built
- **THEN** the prompt does not contain any documented banned example phrase verbatim

