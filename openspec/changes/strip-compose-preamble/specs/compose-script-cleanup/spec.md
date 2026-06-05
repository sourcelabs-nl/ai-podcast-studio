## ADDED Requirements

### Requirement: Tagged scripts are trimmed to the speaker-tag span
For dialogue and interview compositions, the system SHALL strip any text before the first opening speaker tag and after the last closing speaker tag from the composed script before it is stored. The stripping SHALL preserve all speaker turns (including a final turn whose tag differs from the first turn's tag) and SHALL preserve any text between turns. Scripts containing no speaker tags SHALL be returned unchanged.

#### Scenario: Leading LLM preamble removed
- **WHEN** the compose LLM returns "I have enough context. Writing the script now." followed by tagged speaker turns
- **THEN** the stored script starts at the first opening speaker tag

#### Scenario: Trailing text after last turn removed
- **WHEN** the composed script ends with prose after the final closing tag
- **THEN** the stored script ends at the last closing speaker tag

#### Scenario: Final turn with different tag preserved
- **WHEN** the script starts with an `<interviewer>` turn and ends with an `<expert>` turn
- **THEN** the trailing `<expert>` turn is preserved in the stored script

#### Scenario: Untagged script passes through
- **WHEN** the composed script contains no speaker tags
- **THEN** the script is returned unchanged

### Requirement: Monologue scripts drop a leading meta-commentary paragraph
For briefing (monologue) compositions, the system SHALL remove the first paragraph of the composed script only when it is at most 300 characters AND matches known LLM meta-commentary phrasings (writing/drafting/composing the script, having enough/plenty of context or information, "what I need"). Genuine spoken openings SHALL never be removed; when in doubt the paragraph SHALL be kept.

#### Scenario: Writing-the-script preamble removed
- **WHEN** a briefing script starts with "I have plenty of context now. Let me write the script." followed by the spoken opening
- **THEN** the stored script starts at the spoken opening

#### Scenario: Genuine opening kept
- **WHEN** a briefing script opens with a spoken hook (e.g., "What happens when one AI commands a thousand others?")
- **THEN** the first paragraph is kept unchanged

#### Scenario: Long first paragraph kept despite keyword
- **WHEN** the first paragraph exceeds 300 characters and merely mentions the word "script"
- **THEN** the paragraph is kept unchanged
