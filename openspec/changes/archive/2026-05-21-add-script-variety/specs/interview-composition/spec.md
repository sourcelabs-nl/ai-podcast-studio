## ADDED Requirements

### Requirement: Interview prompts use variety rotation

The interview composer prompt SHALL be parameterized by the `PromptVarietyPicker` selection for the current `(podcastId, episodeDate)`. Opening style, transition vocabulary, sign-off shape, and any "coming up" teaser shape MUST come from the picker, not be hard-coded constants or verbatim example strings.

#### Scenario: Different dates produce different prompt scaffolding

- **WHEN** the interview composer builds prompts for the same podcast on two different dates with the same article set
- **THEN** the prompt strings differ in the opening-style, transition-vocabulary, and sign-off-shape sections

### Requirement: Interview prompts contain no verbatim example phrases

The interview composer prompt SHALL NOT include literal sample sentences such as `"But here's where it gets really interesting..."`, `"Coming up: AI agents going rogue..."`, `"Wait, wait — did you say 100x?!"`, or `"Stephan, thanks as always"`. The interruption-style menu SHALL describe categories (excited, skeptical, confused, connecting dots, playful disagreement) without prescribing the exact wording.

#### Scenario: No banned phrases present

- **WHEN** the interview composer prompt is built
- **THEN** the prompt does not contain any of the documented banned example phrases verbatim
