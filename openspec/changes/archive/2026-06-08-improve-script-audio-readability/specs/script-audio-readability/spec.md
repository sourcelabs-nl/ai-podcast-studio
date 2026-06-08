## ADDED Requirements

### Requirement: Numbers verbalized for the ear
Every compose-stage prompt (briefing, dialogue, and interview) SHALL include a shared rule instructing the LLM to verbalize numbers for audio comprehension. The rule SHALL direct the model to round figures to clean values, lead with what a result means before stating the number, prefer plain-language comparisons over raw decimals, voice at most one number per spoken sentence or claim (summarizing additional metrics qualitatively), and de-emphasize benchmark proper-names by describing what a benchmark measures rather than reciting an exact name and score together. The rule SHALL live in one shared place so all three styles share identical wording.

#### Scenario: Numbers rule present in every composer prompt
- **WHEN** a briefing, dialogue, or interview prompt is built
- **THEN** the prompt contains the "NUMBERS FOR THE EAR" guidance, including the instruction to voice at most one number per sentence or claim

### Requirement: Plain-language elaboration for non-experts
Every compose-stage prompt SHALL include a shared rule permitting and encouraging brief, plain-language elaboration on complex or unfamiliar topics for listeners who are not specialists. The rule SHALL allow spending extra sentences to explain what something is, how it works, or its consequences, encourage analogies and defining jargon on first use, and allow using a web search tool (within budget, when available) for outside context. The rule SHALL keep elaboration conversational and reserved for topics that warrant the depth.

#### Scenario: Elaboration rule present in every composer prompt
- **WHEN** a briefing, dialogue, or interview prompt is built
- **THEN** the prompt contains the "EXPLAIN FOR NON-EXPERTS" guidance
