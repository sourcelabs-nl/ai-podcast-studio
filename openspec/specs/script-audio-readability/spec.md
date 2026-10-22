# script-audio-readability Specification

## Purpose
TBD - created by archiving change improve-script-audio-readability. Update Purpose after archive.
## Requirements
### Requirement: Numbers verbalized for the ear
Every compose-stage prompt (briefing, dialogue, and interview) SHALL include a shared rule instructing the LLM to verbalize numbers for audio comprehension. The rule SHALL direct the model to round figures to clean values, lead with what a result means before stating the number, prefer plain-language comparisons over raw decimals, voice at most one number per spoken sentence or claim (summarizing additional metrics qualitatively), and de-emphasize benchmark proper-names by describing what a benchmark measures rather than reciting an exact name and score together. The rule SHALL live in one shared place so all three styles share identical wording.

#### Scenario: Numbers rule present in every composer prompt
- **WHEN** a briefing, dialogue, or interview prompt is built
- **THEN** the prompt contains the "NUMBERS FOR THE EAR" guidance, including the instruction to voice at most one number per sentence or claim

### Requirement: Explain complex concepts for non-experts
Every compose-stage prompt SHALL include a shared rule directing the LLM to actively explain genuinely complex or unfamiliar concepts for listeners who are not specialists. Whenever such a concept comes up, the rule SHALL direct the LLM to explain it in plain language before moving on (what it is, how it works at a high level, and why it matters), define jargon on first use, and reach for everyday analogies. The rule SHALL NOT impose a fixed number of explanations per episode: the limiters SHALL be brevity (conversational, not a lecture, just enough for a non-specialist to follow) and the "genuinely complex or unfamiliar" filter, so the depth scales with the episode's actual content. The rule SHALL allow using a web search tool (within budget, when available) for outside context. The rule SHALL be defined once in `ComposerUtils.kt` and shared verbatim across the three composers.

#### Scenario: Compose prompt contains the explain-for-non-experts directive
- **WHEN** any composer (`BriefingComposer`, `DialogueComposer`, `InterviewComposer`) builds its prompt
- **THEN** the prompt contains the "EXPLAIN FOR NON-EXPERTS" guidance directing the LLM to explain complex concepts in plain language without a fixed count

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
