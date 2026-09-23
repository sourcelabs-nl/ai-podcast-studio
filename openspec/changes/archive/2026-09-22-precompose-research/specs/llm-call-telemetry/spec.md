## ADDED Requirements

### Requirement: Each request records the served provider and its reasoning tokens

Each recorded LLM request SHALL store the name of the upstream provider that served it, as reported in the response's `provider` field, and the number of reasoning tokens, as reported in `usage.completion_tokens_details.reasoning_tokens`. Either value SHALL be null when the response does not report it, including a cache hit and a failed request. The per-episode request list (`GET /llm/calls/episodes/{id}`) SHALL include both values for every request.

#### Scenario: OpenRouter request
- **WHEN** a compose request is answered by OpenRouter with `provider` "Google" and 21,000 reasoning tokens
- **THEN** the recorded request holds served provider "Google" and 21,000 reasoning tokens, and the episode's request list shows both

#### Scenario: Cache hit
- **WHEN** a request is served from the LLM cache
- **THEN** its served provider and reasoning tokens are null

### Requirement: The research-plan stage is reported

The latency report SHALL include the `research-plan` stage, against the request timeout of the filter stage whose model it runs on.

#### Scenario: Plan requests in the report
- **WHEN** latency is requested for an episode whose research stage made a plan call
- **THEN** the response reports the `research-plan` stage with one sample
