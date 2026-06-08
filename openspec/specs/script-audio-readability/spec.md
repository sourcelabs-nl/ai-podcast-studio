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

