## ADDED Requirements

### Requirement: Speaker-tag retry carries the rejected response
When speaker-tag validation rejects a composed multi-speaker script, the retry request SHALL contain the original conversation, followed by the rejected response as an assistant message, followed by the correction as a user message.

#### Scenario: Retry after an untagged script
- **WHEN** the compose LLM returns a script without speaker tags
- **THEN** the retry request contains that script as an assistant message
- **AND** the correction is the only message after it

### Requirement: Multi-speaker script requires at least two speakers
Speaker-tag validation SHALL reject a script in which fewer than two distinct speaker roles have a turn (or fewer than the number of configured roles, when that is lower), and retry with a correction asking for the complete episode script from the source material.

#### Scenario: Single tagged turn is rejected
- **WHEN** the compose LLM returns a single correctly tagged `<expert>` turn
- **THEN** validation fails with "only 1 speaker(s)" and the request is retried

#### Scenario: Two speakers pass
- **WHEN** the script contains turns for both `<interviewer>` and `<expert>`
- **THEN** validation passes without a retry
