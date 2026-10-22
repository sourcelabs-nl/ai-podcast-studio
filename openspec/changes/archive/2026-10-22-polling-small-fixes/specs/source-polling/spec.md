## ADDED Requirements

### Requirement: A dropped connection is retried once
When fetching a source fails and the failure's cause chain contains a `java.net.SocketException` (connection reset, refused or closed), the poller SHALL repeat the fetch once after `app.source.connection-retry-delay` (default 3 seconds). An HTTP error status or a timeout SHALL NOT be retried. Only the fetch SHALL be repeated. A retry that succeeds SHALL count as a successful poll, and a drop that persists on the retry SHALL count as one transient failure.

#### Scenario: A dropped connection is retried and succeeds
- **WHEN** the first fetch of a source fails with a connection reset and the retry succeeds
- **THEN** the poll counts as a success and no failure is recorded

#### Scenario: A drop that persists counts once
- **WHEN** both the fetch and its retry fail with a connection reset
- **THEN** one transient failure is recorded for the source

#### Scenario: An HTTP error status is not retried
- **WHEN** a fetch fails with an HTTP error status
- **THEN** the fetch is not repeated and the failure is recorded
