# Proposal

## Why

On 2026-10-21 a single `Connection reset` on the 16:00 catch-up poll of a Narro X feed counted as a failed poll, so the source was behind the window and generation was deferred. Dropped connections usually clear within seconds. The same deferral was also logged at info on every per-minute re-check, flooding the log with identical lines.

## What Changes

- `SourcePoller` retries a fetch once after `app.source.connection-retry-delay` (default 3 seconds) when the failure's cause chain contains a `java.net.SocketException` (connection reset, refused, closed). HTTP error statuses and timeouts are not retried. Only the fetch is repeated, nothing has been stored at that point. A drop that persists on the retry counts as one transient failure.
- `BriefingGenerationScheduler` logs "is due for briefing generation" and "Deferring generation" at info only the first time per podcast slot, and at debug on each per-minute re-check.

## Capabilities

### New Capabilities

### Modified Capabilities
- `source-polling`: adds "A dropped connection is retried once".
- `episode-article-window`: "Generation waits for the pollers to cover the window" gains the log-level rule for repeated deferrals.

## Impact

- `SourcePoller`, `SourceProperties` (`connectionRetryDelay`), `application.yaml`, `BriefingGenerationScheduler`. No schema or API change.
- Tests: `SourcePollerFailureTrackingTest`, `BriefingGenerationSchedulerTest`.
