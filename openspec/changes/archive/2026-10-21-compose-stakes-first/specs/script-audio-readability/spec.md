## ADDED Requirements

### Requirement: Stakes before mechanism
Every compose-stage prompt (briefing, dialogue, interview) SHALL include a shared rule that each topic opens with what changes for the listener (what they can now do, what they should stop trusting, or what to keep an eye on) and only then explains how it works, as much as the listener needs to follow that consequence. The rule SHALL direct the LLM to tell each topic as a story with a point rather than a summary of a paper or press release, and SHALL state that an item with no consequence statable in one sentence gets one sentence or is left out. The rule SHALL be defined once in `ComposerUtils.kt` and shared verbatim across the three composers.

#### Scenario: Every composer prompt carries the stakes rule
- **WHEN** `BriefingComposer`, `DialogueComposer` or `InterviewComposer` builds its prompt
- **THEN** the prompt contains the "STAKES BEFORE MECHANISM" guidance, including the allowance to give a consequence-less item one sentence or leave it out

### Requirement: Multi-speaker prompts ask so what
The dialogue and interview prompts SHALL include a shared "ASK SO WHAT" rule: at least once per full segment, a speaker (the interviewer in the interview style, one of the speakers in the dialogue style) asks what a story means in practice, such as whether a claim holds up, who should care, or what changes for someone who works with it. The rule SHALL state that a turn which only names the next item is not a question and that a topic is entered through its consequence or a genuine reaction instead. The briefing prompt SHALL NOT include this rule, since it has a single speaker.

#### Scenario: Dialogue and interview prompts ask so what
- **WHEN** `DialogueComposer` or `InterviewComposer` builds its prompt
- **THEN** the prompt contains the "ASK SO WHAT" guidance, including that a bare bridge turn is not a question

#### Scenario: Briefing prompt omits so what
- **WHEN** `BriefingComposer` builds its prompt
- **THEN** the prompt does not contain "ASK SO WHAT"

### Requirement: Segment length is not fixed
The interview and dialogue prompts SHALL keep topic segments concise without prescribing a fixed duration per segment, and SHALL tell the LLM to give a story that matters to the listener room to breathe instead of holding every topic to the same length.

#### Scenario: No fixed segment duration
- **WHEN** the interview or dialogue prompt is built
- **THEN** it does not contain "roughly 60-90 seconds each" and does contain the room-to-breathe instruction
