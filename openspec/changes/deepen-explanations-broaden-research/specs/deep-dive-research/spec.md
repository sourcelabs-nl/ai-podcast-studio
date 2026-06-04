## MODIFIED Requirements

### Requirement: Compose prompt nudges deep-dive usage
When `podcast.deepDiveEnabled=true`, the compose-stage prompts (monologue, dialogue, interview) SHALL include instructions directing the LLM to identify the 2-3 most newsworthy stories and call `webSearch` roughly once each for outside context, aiming to spend 2-3 of its 3-call episode budget across those standout stories rather than all on one. The subtopics-aware variant SHALL additionally restrict `webSearch` to stories covered in a full segment (never rapid-fire stories) and prefer higher-weight subtopics when stories are comparable. When `deepDiveEnabled=false`, the prompts MUST NOT reference `webSearch`.

#### Scenario: Enabled podcast prompt references webSearch
- **WHEN** an episode is composed for a podcast with `deepDiveEnabled=true`
- **THEN** the prompt text references `webSearch` and instructs the LLM to cover the 2-3 most newsworthy stories within its 3-call budget

#### Scenario: Disabled podcast prompt omits webSearch
- **WHEN** an episode is composed for a podcast with `deepDiveEnabled=false`
- **THEN** the prompt text does not reference `webSearch`
