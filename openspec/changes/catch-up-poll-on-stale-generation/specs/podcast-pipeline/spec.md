## ADDED Requirements

### Requirement: Catch-up poll before stale scheduled generation
When a podcast is due for scheduled briefing generation, the system SHALL ensure source data is fresh before composing. If the last completed poll round is older than a configurable threshold (`app.source.stale-round-threshold-minutes`, default 10 minutes) or no poll round has completed in the current process, the generator SHALL first run a synchronous catch-up poll of that podcast's sources and wait for it to finish, then generate. If the last poll round is within the threshold, generation SHALL proceed immediately without an extra poll. This prevents composing an episode against stale data after the machine was asleep or offline through the scheduled time.

#### Scenario: Stale polling triggers a catch-up poll
- **WHEN** a podcast is due and the last poll round completed more than the threshold ago (e.g. the machine was asleep)
- **THEN** the generator polls that podcast's sources to completion first, then generates the briefing

#### Scenario: No poll round recorded yet
- **WHEN** a podcast is due and no poll round has completed in the current process (e.g. just after restart)
- **THEN** the generator runs a catch-up poll before generating

#### Scenario: Fresh polling skips the catch-up poll
- **WHEN** a podcast is due and the last poll round completed within the threshold
- **THEN** the generator proceeds to generate immediately, with no extra poll
