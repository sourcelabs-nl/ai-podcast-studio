## 1. Replace staleness window with same-day logic

- [x] 1.1 In `BriefingGenerationScheduler.kt`: remove the `STALENESS_WINDOW` companion constant. Replace the `startFrom` calculation and the stale-trigger skip loop with same-calendar-day logic: compute today's date in the podcast's timezone, skip triggers whose date is before today (log WARN), and fire the first trigger that falls on today.
- [x] 1.2 In `BriefingGenerationSchedulerTest.kt`: update existing staleness tests and add new tests for same-day catch-up scenarios (same-day fires hours later, previous-day skipped, timezone-aware catch-up, new podcast first-day catch-up).

## 2. Verify

- [x] 2.1 Run `mvn test` to confirm all tests pass.
