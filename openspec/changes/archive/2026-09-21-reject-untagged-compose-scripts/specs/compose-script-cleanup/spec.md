## MODIFIED Requirements

### Requirement: Invalid speaker tags are rejected before storage with a self-correcting retry
For dialogue and interview compositions, the system SHALL validate the compose LLM's response before the script is accepted, rejecting it when either:
- a speaker tag is present that is not one of the podcast's allowed roles (the podcast's configured `ttsVoices` keys for dialogue, or `interviewer`/`expert` for interview), or
- the response carries no speaker tag for any allowed role at all.

When a response is rejected, the system SHALL append an error to the LLM request describing the failure and identifying the allowed tags, and re-issue the request, up to 2 retries (3 attempts total). If no attempt produces an acceptable response, the system SHALL fail the compose call with an error describing the failure rather than returning the script.

When a response is rejected for both reasons at once, the system SHALL report the invalid tag(s), since naming the offending tag gives the model more to correct than reporting the absence of tags.

For the purpose of the no-speaker-tag check, a tag SHALL be considered present when an opening tag for an allowed role appears with either angle-bracket (`<expert>`) or square-bracket (`[expert]`) delimiters, whether or not a matching closing tag follows. Both forms are recovered downstream, so rejecting them would re-run a compose the pipeline can already voice.

This validation SHALL apply only to multi-speaker compositions. Briefing (monologue) scripts are a single voice and legitimately carry no speaker tags.

#### Scenario: Leaked tool-call artifact rejected and self-corrected
- **WHEN** the compose LLM's first response contains a `<function_results>` tag alongside valid `<interviewer>`/`<expert>` tags
- **THEN** the system re-prompts the model with an error naming `function_results` as invalid and `interviewer`/`expert` as the allowed tags
- **AND** if the model's next response contains only valid tags, that response is accepted

#### Scenario: Untagged script rejected and self-corrected
- **WHEN** the compose LLM's first response is a well-formed alternating dialogue containing no speaker tag at all
- **THEN** the system re-prompts the model with an error stating the response contained no speaker tags and naming the allowed tags
- **AND** if the model's next response contains valid tags, that response is accepted

#### Scenario: Only valid tags present on the first attempt
- **WHEN** the compose LLM's response contains only tags that are in the podcast's allowed role set
- **THEN** the system accepts the response without retrying

#### Scenario: Square-bracketed opener is not treated as untagged
- **WHEN** the compose LLM's response opens a turn with `[interviewer]` and closes it with `</interviewer>`
- **THEN** the system accepts the response without retrying, leaving the downstream normalization to recover the turn

#### Scenario: Model never self-corrects on an invalid tag
- **WHEN** the compose LLM's response contains an invalid tag on every attempt through the retry budget
- **THEN** the system throws an error identifying the invalid tag(s) instead of returning the script

#### Scenario: Model never produces speaker tags
- **WHEN** the compose LLM's response contains no speaker tag on every attempt through the retry budget
- **THEN** the system throws an error stating the script has no speaker tags instead of returning the script

#### Scenario: Response still awaiting tool call resolution
- **WHEN** the compose LLM's response still has pending tool calls
- **THEN** the system does not validate speaker tags on that response, since its text is not the final script

#### Scenario: Model never self-corrects
- **WHEN** the compose LLM's response contains an invalid tag on every attempt through the retry budget
- **THEN** the system throws an error identifying the invalid tag(s) instead of returning the script

## ADDED Requirements

### Requirement: Multi-speaker prompts state that speaker tags are mandatory
The dialogue and interview compose prompts SHALL instruct the model that every line of spoken text, including the cold open and the sign-off, must be wrapped in one of the allowed speaker tags, and that an untagged script cannot be voiced. This rule SHALL be stated ahead of the existing rule about which delimiters a speaker tag uses, and SHALL be defined in one shared place so both prompts carry identical wording.

#### Scenario: Dialogue prompt carries the mandatory-tags rule
- **WHEN** a dialogue compose prompt is built for a podcast
- **THEN** the prompt states that speaker tags are mandatory for every line of spoken text and lists the podcast's allowed tags

#### Scenario: Interview prompt carries the mandatory-tags rule
- **WHEN** an interview compose prompt is built
- **THEN** the prompt states that speaker tags are mandatory for every line of spoken text and lists `interviewer` and `expert` as the allowed tags
