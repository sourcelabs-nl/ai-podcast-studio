## ADDED Requirements

### Requirement: SQLite tolerates overlapping writes via busy_timeout
The application's SQLite datasource SHALL be configured with a `busy_timeout` of at least 30000ms (via the JDBC URL) and SHALL run in WAL journal mode.

SQLite permits only one writer at a time; WAL mode keeps reads non-blocking but does not allow concurrent writers. When a connection encounters a write lock held by another connection, the `busy_timeout` causes it to wait and retry (up to the timeout) rather than failing immediately with `SQLITE_BUSY`. Because the application's write transactions are short, a 30s timeout provides ample headroom for overlapping writes (e.g. a large article-aggregation batch overlapping source polling) to serialize successfully.

The datasource SHALL NOT pin the Hikari pool to a single connection (`maximum-pool-size: 1`): the coroutine-based generation pipeline acquires several connections concurrently, and a single-connection pool starves it, causing connection-acquisition timeouts.

#### Scenario: Concurrent write-heavy operations do not raise SQLITE_BUSY
- **WHEN** a large article-aggregation batch runs while another database write (e.g. source polling or an episode update) is in progress
- **THEN** the later write waits on the busy handler and completes without a `SQLITE_BUSY` failure

#### Scenario: Generation pipeline is not starved of connections
- **WHEN** the generation pipeline's persist phase acquires several database connections concurrently across coroutines
- **THEN** connections are available from the pool and no "Connection is not available" acquisition timeout occurs

### Requirement: No database transaction is held across slow I/O
Service methods SHALL NOT hold a database transaction (`@Transactional`) across slow I/O operations such as LLM calls, TTS synthesis, or external HTTP requests. Holding a transaction across such operations pins a pooled JDBC connection and the SQLite write lock for the operation's full duration, starving the pool and blocking all other writers.

The episode generation and regeneration methods that run TTS or LLM work between their database writes SHALL perform those writes as individual atomic operations outside any enclosing transaction. Because the writes are idempotent and the episode is retryable, partial progress (e.g. a persisted script before a failed TTS step) is acceptable and aids resumption.

#### Scenario: Episode generation does not hold a transaction across TTS
- **WHEN** an episode is generated and the TTS pipeline runs for an extended period
- **THEN** no database connection or SQLite write lock is held for the duration of the TTS call, and concurrent writers are not blocked

#### Scenario: Recap regeneration does not hold a transaction across the LLM call
- **WHEN** an episode's recap is regenerated via an LLM call
- **THEN** the LLM call runs outside any transaction, and only the resulting writes are committed (each atomically)
