## ADDED Requirements

### Requirement: The per-episode request list shows where a request's time went

The dashboard's per-episode request list SHALL show, for each request that has generation stats, its
startup, reasoning and writing time, output tokens per second, served provider and the number of
upstream attempts, and SHALL show the attempts' providers and statuses when there was more than one.
A request without generation stats SHALL show those values as unavailable rather than as zero.

#### Scenario: Slow request with a fallback
- **WHEN** a compose request took 526 s with two upstream attempts
- **THEN** its row shows startup, reasoning and writing time, tokens per second, the serving
  provider, and both attempts with their statuses

#### Scenario: Request without stats
- **WHEN** a request has no generation stats
- **THEN** its breakdown columns show a placeholder, not 0
