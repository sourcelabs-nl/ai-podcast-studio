## Why

The briefing generation scheduler currently skips missed triggers if the app comes back online more than 30 minutes after the scheduled time. Since the app runs on a laptop that may be closed/off during the scheduled hour, this means episodes are silently skipped. A missed trigger should still fire as long as it is the same calendar day in the podcast's timezone.

## What Changes

- Replace the fixed 30-minute staleness window with same-calendar-day logic: a missed cron trigger fires if it falls on today's date (in the podcast's timezone), and is skipped if it falls on a previous day.
- Remove the `STALENESS_WINDOW` constant.
- Update existing tests and add new tests for same-day catch-up scenarios.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `podcast-pipeline`: The staleness rule changes from "within 30 minutes" to "same calendar day in the podcast's timezone".

## Impact

- `BriefingGenerationScheduler.kt`: staleness/catch-up logic rewritten.
- `BriefingGenerationSchedulerTest.kt`: existing staleness tests updated, new same-day catch-up tests added.
- `openspec/specs/podcast-pipeline/spec.md`: requirement and scenarios updated to reflect same-day semantics.
