## ADDED Requirements

### Requirement: The latency view is ordered slowest first
The stage table SHALL be ordered by p99 descending, and the request list SHALL be
ordered by duration descending. The point of this view is to find what was slow, so
the slowest row is the first one read rather than one found by scanning.

A stage that recorded no request has no timing to rank and SHALL sort last rather
than being dropped. A cache hit performed no request, so its duration SHALL NOT
rank it among the slow ones.

#### Scenario: The slowest stage is first
- **WHEN** the stage table renders for an episode whose compose stage is its slowest
- **THEN** the compose row appears above the faster stages

#### Scenario: A stage without requests sorts last
- **WHEN** a stage recorded no requests
- **THEN** its row is still shown, below every stage that has timings

#### Scenario: The slowest request is first
- **WHEN** the request list renders
- **THEN** the longest-running request is the first row, and cache hits are not ranked as slow
