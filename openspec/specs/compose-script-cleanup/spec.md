# compose-script-cleanup Specification

## Purpose
TBD - created by archiving change strip-compose-preamble. Update Purpose after archive.
## Requirements
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

### Requirement: Invalid speaker tags are rejected before storage with a self-correcting retry
For dialogue and interview compositions, the system SHALL validate that every speaker tag present in the compose LLM's response is one of the podcast's allowed roles (the podcast's configured `ttsVoices` keys for dialogue, or `interviewer`/`expert` for interview) before the script is accepted. If an invalid tag is found, the system SHALL append an error to the LLM request identifying the invalid tag(s) and the allowed tags, and re-issue the request, up to 2 retries (3 attempts total). If no attempt produces only valid tags, the system SHALL fail the compose call with an error identifying the invalid tag(s) rather than returning the script.

#### Scenario: Leaked tool-call artifact rejected and self-corrected
- **WHEN** the compose LLM's first response contains a `<function_results>` tag alongside valid `<interviewer>`/`<expert>` tags
- **THEN** the system re-prompts the model with an error naming `function_results` as invalid and `interviewer`/`expert` as the allowed tags
- **AND** if the model's next response contains only valid tags, that response is accepted

#### Scenario: Only valid tags present on the first attempt
- **WHEN** the compose LLM's response contains only tags that are in the podcast's allowed role set
- **THEN** the system accepts the response without retrying

#### Scenario: Model never self-corrects
- **WHEN** the compose LLM's response contains an invalid tag on every attempt through the retry budget
- **THEN** the system throws an error identifying the invalid tag(s) instead of returning the script

#### Scenario: Response still awaiting tool call resolution
- **WHEN** the compose LLM's response still has pending tool calls
- **THEN** the system does not validate speaker tags on that response, since its text is not the final script

