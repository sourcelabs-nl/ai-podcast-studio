## ADDED Requirements

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
