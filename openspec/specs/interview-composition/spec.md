# Capability: Interview Composition

## Purpose

Interview-style dialogue composition for podcast scripts, producing natural asymmetric conversations between an interviewer and expert with XML speaker tags for TTS processing.
## Requirements
### Requirement: InterviewComposer generates asymmetric speaker-tagged scripts
The system SHALL provide an `InterviewComposer` component that generates interview-style dialogue scripts with two fixed roles: `interviewer` and `expert`. The interviewer SHALL act as an active conversational partner — asking questions, bridging topics, reacting, challenging, and providing commentary (~35% of total words). The expert SHALL deliver the news content, context, and analysis (~65% of total words). The output SHALL use XML-style speaker tags `<interviewer>` and `<expert>`. The composer SHALL use the `compose` model (resolved via `ModelResolver`).

#### Scenario: Interview script generated with two speakers
- **WHEN** the `InterviewComposer` composes a script for a podcast with `ttsVoices: {"interviewer": "id1", "expert": "id2"}`
- **THEN** the output contains alternating `<interviewer>` and `<expert>` tags with the interviewer asking questions and the expert delivering content

#### Scenario: Interviewer has significant airtime
- **WHEN** the `InterviewComposer` generates a script
- **THEN** interviewer turns comprise approximately 35% of total words, including questions, reactions, challenges, and commentary, while expert turns comprise approximately 65% with substantive news content and analysis

#### Scenario: Composer uses compose model
- **WHEN** the `InterviewComposer` is invoked
- **THEN** it resolves and uses the `compose` stage model via `ModelResolver`

#### Scenario: Tags are not stripped from output
- **WHEN** the LLM produces an interview script with `<interviewer>` and `<expert>` tags
- **THEN** the tags are preserved in the returned script

### Requirement: InterviewComposer prompt engineering
The InterviewComposer prompt SHALL instruct the model on script structure and engagement, including the hook opening and conversational coherence.

**Hook opening:** The prompt SHALL instruct the interviewer to NOT start with a standard welcome. Instead, the interviewer SHALL open with a provocative statement, surprising fact, or compelling question drawn from the most interesting article of the day, then transition into the regular introduction.

**No empty setup turns:** The prompt SHALL forbid contentless setup turns. When a speaker announces or teases a specific point (a caveat, skeptical flag, question, fact, or statistic), that same speaker SHALL state its substance in the same turn. A handoff to the other speaker is permitted only when that speaker adds genuinely new information, not when they complete a point the first speaker merely gestured at.

#### Scenario: Hook opening instead of standard welcome
- **WHEN** the interview prompt is built
- **THEN** it instructs the interviewer to open with a hook rather than a standard welcome

#### Scenario: No contentless setup turns
- **WHEN** the interview prompt is built
- **THEN** it instructs the model that a speaker who teases a specific point must state that point in the same turn rather than handing its substance to the other speaker

### Requirement: Interview style routing in pipeline
The LLM pipeline SHALL route `style: "interview"` to the `InterviewComposer`. The selection SHALL happen in the pipeline orchestration layer (`LlmPipeline`).

#### Scenario: Interview style uses InterviewComposer
- **WHEN** a podcast has `style: "interview"`
- **THEN** the pipeline uses `InterviewComposer` for script generation

#### Scenario: Dialogue style still uses DialogueComposer
- **WHEN** a podcast has `style: "dialogue"`
- **THEN** the pipeline uses `DialogueComposer` for script generation

#### Scenario: Monologue styles still use BriefingComposer
- **WHEN** a podcast has `style: "news-briefing"`
- **THEN** the pipeline uses `BriefingComposer` for script generation

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

