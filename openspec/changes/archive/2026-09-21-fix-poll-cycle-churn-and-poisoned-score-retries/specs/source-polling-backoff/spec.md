## MODIFIED Requirements

### Requirement: Failure tracking on source
The system SHALL track consecutive failures on each source. The `Source` entity SHALL have a `consecutiveFailures` field (default 0), a `lastFailureType` field (nullable, values: `"transient"` or `"permanent"`), and a `disabledReason` field (nullable). On each failed poll, `consecutiveFailures` SHALL be incremented by 1 and `lastFailureType` SHALL be set to the classified error type. On a successful poll, `consecutiveFailures` SHALL be reset to 0 and `lastFailureType` SHALL be cleared to null.

Only a failure to fetch or ingest content SHALL count as a failed poll. Persisting the source's post-poll state is not part of the fetch, and `sources` is a versioned entity, so a poll that overlaps another poll of the same source can lose the optimistic-lock race and fail its update with `OptimisticLockingFailureException`. Losing that race SHALL NOT be recorded as a poll failure: it must not increment `consecutiveFailures`, set `lastFailureType`, or contribute toward auto-disabling, because the source itself is healthy.

On `OptimisticLockingFailureException` the system SHALL re-read the source row and reapply the same state update once. The update SHALL be computed from the re-read row rather than the stale copy, so `consecutiveFailures` counts up from the value the concurrent writer recorded and the auto-disable threshold is evaluated against the true running count. If the re-read finds the source deleted, the poll state SHALL be discarded and a warning logged, rather than an error raised.

#### Scenario: First failure increments counter
- **WHEN** a source poll fails for the first time with a transient error
- **THEN** `consecutiveFailures` is set to 1 and `lastFailureType` is set to `"transient"`

#### Scenario: Repeated failures increment counter
- **WHEN** a source with `consecutiveFailures = 3` fails again
- **THEN** `consecutiveFailures` is set to 4

#### Scenario: Success resets failure tracking
- **WHEN** a source with `consecutiveFailures = 3` polls successfully
- **THEN** `consecutiveFailures` is set to 0 and `lastFailureType` is set to null

#### Scenario: Lost optimistic-lock race is not a poll failure
- **WHEN** a source is fetched successfully but its state update fails with `OptimisticLockingFailureException` because a concurrent poll of the same source updated the row first
- **THEN** the update is retried against the re-read row, the source remains enabled, and `consecutiveFailures` is not incremented

#### Scenario: Failure counter on retry counts from the re-read row
- **WHEN** a poll fails to fetch, its state update loses the optimistic-lock race, and the re-read row shows `consecutiveFailures = 4`
- **THEN** the retried update sets `consecutiveFailures` to 5

#### Scenario: Source deleted mid-poll discards the poll state
- **WHEN** a source's state update fails with `OptimisticLockingFailureException` and the source no longer exists
- **THEN** no further save is attempted, a warning is logged, and no exception escapes the poll
