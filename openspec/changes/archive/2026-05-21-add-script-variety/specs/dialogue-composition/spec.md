## ADDED Requirements

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
