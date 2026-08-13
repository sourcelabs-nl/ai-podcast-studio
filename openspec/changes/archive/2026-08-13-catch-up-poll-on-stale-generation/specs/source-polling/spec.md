## ADDED Requirements

### Requirement: Poll round completion tracking
The system SHALL record the wall-clock time at which each successful poll round completes, kept in memory on the polling scheduler. Before any round has completed in the current process (for example immediately after startup), the recorded time SHALL be absent. This timestamp lets other components detect whether polling has been running recently (versus the process having been asleep or offline).

#### Scenario: Timestamp set after a poll round
- **WHEN** a poll round completes
- **THEN** the last-poll-round-completed time is set to the current time

#### Scenario: Timestamp absent before the first round
- **WHEN** the process has started but no poll round has completed yet
- **THEN** the last-poll-round-completed time is absent

### Requirement: On-demand poll of a single podcast's sources
The system SHALL support polling all enabled sources of a single podcast on demand, to completion, reusing the same host-grouped polling and per-host delay behavior as the scheduled poll loop. A blocking entry point SHALL be available for callers that are not themselves coroutines.

#### Scenario: Poll a podcast's sources on demand
- **WHEN** an on-demand poll is requested for a podcast
- **THEN** only that podcast's enabled sources are polled, and the call returns after they have all been polled

#### Scenario: Podcast with no enabled sources
- **WHEN** an on-demand poll is requested for a podcast that has no enabled sources
- **THEN** the call returns without polling anything
