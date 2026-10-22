## ADDED Requirements

### Requirement: Feed fetches have connect and read timeouts

The RSS/Atom feed fetcher SHALL apply a connect timeout (`app.source.feed-connect-timeout`, default 10 seconds) and a read timeout (`app.source.feed-read-timeout`, default 30 seconds) to its HTTP client. A request that exceeds either SHALL fail as a transient error.

#### Scenario: A feed that never responds
- **WHEN** a feed server accepts the connection but never sends a response
- **THEN** the fetch fails on the read timeout as a transient error instead of waiting indefinitely

### Requirement: One source's poll is bounded in time

The scheduler SHALL bound each source's whole poll with `app.source.poll-timeout` (default 10 minutes). A poll still running after it SHALL be interrupted and logged with the source id and URL, and the poll round SHALL continue with the remaining sources.

#### Scenario: A hanging poll does not hold up the round
- **WHEN** one source's poll blocks past the poll timeout
- **THEN** it is interrupted and a warning is logged, and the other sources in the round are still polled
