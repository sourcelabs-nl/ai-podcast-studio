## Context

The `BriefingGenerationScheduler` uses a fixed 30-minute staleness window to decide whether a missed cron trigger should still fire. Since the app runs on a laptop that may be off during the scheduled hour, triggers are frequently missed and silently skipped.

## Goals / Non-Goals

**Goals:**
- A missed cron trigger fires when the app comes back online, as long as it is the same calendar day in the podcast's timezone.
- Triggers from previous days are skipped (logged at WARN).

**Non-Goals:**
- Persisting missed triggers across restarts (the cron expression and `lastGeneratedAt` already provide enough state).
- Changing the 60-second check loop interval.
- Changing source polling behavior.

## Decisions

- **Same-day check replaces staleness window**: Compare the trigger's date to today's date in the podcast's timezone. This is simpler and more intuitive than a duration-based window.
- **No new configuration**: The "same day" rule is unconditional. No need for a configurable window.
- **Start-from logic simplified**: When searching for actionable triggers, start from `lastGeneratedAt` (or start-of-day for new podcasts). Skip triggers whose date is before today. Fire the first trigger that falls on today.

## Risks / Trade-offs

- If a podcast's cron is set to run multiple times per day (e.g., every 6 hours) and the app was off for most of the day, all same-day triggers that were missed will be evaluated. Only the most recent past trigger will fire because `lastGeneratedAt` gets updated after each generation. This is acceptable: at most one catch-up generation per restart.
