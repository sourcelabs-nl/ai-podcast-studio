## REMOVED Requirements

### Requirement: The latency view is ordered slowest first
**Reason**: The request list is ordered by time, which the new requirement below describes.
**Migration**: None; the stage table order is unchanged.

## ADDED Requirements

### Requirement: The latency view orders stages slowest first and requests newest first
The stage table SHALL be ordered by p99 descending, so the slowest stage is the first one read rather than one found by scanning. The request list SHALL be ordered by start time, newest first, so the requests of a run read in the order they were made.

A stage that recorded no request has no timing to rank and SHALL sort last rather than being dropped.

#### Scenario: The slowest stage is first
- **WHEN** the stage table renders for an episode whose compose stage is its slowest
- **THEN** the compose row appears above the faster stages

#### Scenario: A stage without requests sorts last
- **WHEN** a stage recorded no requests
- **THEN** its row is still shown, below every stage that has timings

#### Scenario: The newest request is first
- **WHEN** the request list renders for an episode whose compose call timed out and was retried
- **THEN** the retry appears above the timed-out call, and both appear above the earlier research-plan call
