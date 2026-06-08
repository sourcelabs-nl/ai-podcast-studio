## Why

Episode generation intermittently failed with `SQLITE_BUSY: database is locked`, and then (after a wrong first attempt) with Hikari "Connection is not available" timeouts. Two distinct problems were at play:

1. **busy_timeout too low.** SQLite permits only one writer at a time (WAL makes reads non-blocking but does not allow concurrent writers). With `busy_timeout=5000`, a write that hit a lock held longer than 5s failed with `SQLITE_BUSY` (e.g. a large article-aggregation batch overlapping source polling). This killed the 2026-06-08 auto-generated episode mid-aggregation.

2. **`@Transactional` held across slow I/O.** `createEpisodeFromPipelineResult`, `finalizeEpisode`, `regenerateRecap`, and `regenerateAllShowNotes` held a transaction (and thus a JDBC connection and the SQLite write lock) across the TTS pipeline and/or recap LLM call — operations that take many seconds to minutes. That pins the write lock far beyond any `busy_timeout`, blocking every other writer.

A single-connection pool (`maximum-pool-size: 1`) was tried first but is the wrong fix: the coroutine generation pipeline needs several connections concurrently, so one long-held connection starved the rest and Hikari failed acquisition after 30s — the episode failed in the persist phase even though the LLM steps succeeded.

## What Changes

- Raise the SQLite `busy_timeout` from 5000ms to 30000ms (in the JDBC URL) so a connection that hits a write lock waits and retries instead of failing fast with `SQLITE_BUSY`.
- Keep the default multi-connection Hikari pool (no `maximum-pool-size: 1`) so the coroutine pipeline is not starved.
- Remove `@Transactional` from the four episode methods that run TTS/LLM between their writes (`createEpisodeFromPipelineResult`, `finalizeEpisode`, `regenerateRecap`, `regenerateAllShowNotes`). Each write is atomic on its own; the writes are idempotent and the episode is retryable. Persisting the script before TTS is also desirable — a TTS failure can now be resumed instead of rolling back the generated script.

## Capabilities

### New Capabilities

- `database-write-contention`: Defines how the SQLite datasource tolerates overlapping writes and forbids holding transactions across slow I/O.

### Modified Capabilities

(none)

## Impact

- `src/main/resources/application.yaml` (`spring.datasource.url` busy_timeout)
- `src/main/kotlin/com/aisummarypodcast/podcast/EpisodeService.kt` (4 methods de-annotated)
- No schema or API changes. Requires an application restart to take effect.
