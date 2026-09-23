## ADDED Requirements

### Requirement: Compose stage totals cover every attempt

The compose stage's persisted input tokens, output tokens and cost SHALL be the sum over every request the stage made for the script, including the attempts the speaker-tag validation discarded and re-issued, not only the final request.

#### Scenario: One validation retry
- **WHEN** the first compose request returns 10,000 output tokens with invalid speaker tags and the re-issued request returns 9,000
- **THEN** the episode's compose output tokens are 19,000

### Requirement: The research-plan call is part of the episode's research cost

The episode cost breakdown SHALL add the cost of the episode's recorded `research-plan` requests to the research row, alongside the Tavily search cost. The research row's call count SHALL remain the number of web searches.

#### Scenario: Research row includes the plan
- **WHEN** an episode made 3 web searches at 1 cent each and a plan request that cost 0.2 cents
- **THEN** the research row reports 3 calls and 3.2 cents, and the total includes it
