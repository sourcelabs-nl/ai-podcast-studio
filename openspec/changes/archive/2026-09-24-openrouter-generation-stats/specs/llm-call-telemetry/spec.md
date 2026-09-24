## ADDED Requirements

### Requirement: Each request records its generation id and generation stats

Each recorded OpenRouter request SHALL store the generation id from the response, and SHALL carry
the generation stats defined by the `llm-generation-stats` capability once they have been fetched.
Both SHALL be null for a cache hit, a failed request, and a request to any other provider. The
per-episode request list (`GET /llm/calls/episodes/{id}`) SHALL include, for every request, the time
until the first answer token, generation time, native completion and reasoning tokens, finish reason,
upstream attempts and the request's phases (startup, reasoning, writing, tokens per second), each
null when not available. Generation stats SHALL be retained and deleted together with
the record they belong to.

#### Scenario: Episode request list after the stats arrived
- **WHEN** an episode's compose request has had its generation stats fetched
- **THEN** its entry in the episode's request list shows the time until the first answer token,
  generation time, native token counts, finish reason, attempts and phases

#### Scenario: Retention
- **WHEN** a request record ages past the retention window
- **THEN** it is deleted together with its generation stats
