## Why

Newly-added sources were not polled for up to a full `pollIntervalMinutes` after being created, and users had to restart the application to trigger their first fetch. The startup-jitter logic was running on every polling cycle instead of only at application startup, assigning each new source a synthetic `lastPolled` that pushed its first poll well into the future.

## What Changes

- Gate `SourcePollingScheduler.applyStartupJitter` so it runs only on the first polling cycle after `ApplicationReadyEvent`.
- Sources added mid-run keep `lastPolled = null` and become due on the next ~60s polling cycle, matching user expectations.
- Existing startup behavior (jitter spreading the initial wave of polls across the configured interval) is preserved.

## Capabilities

### New Capabilities
- _(none)_

### Modified Capabilities
- `source-polling`: tighten the startup-jitter requirement so it explicitly applies only once at startup, not on every cycle.

## Impact

- Code: `src/main/kotlin/com/aisummarypodcast/scheduler/SourcePollingScheduler.kt`
- Tests: `src/test/kotlin/com/aisummarypodcast/scheduler/SourcePollingSchedulerTest.kt` (new test added)
- No API, schema, or configuration changes.
