## Context

The application has a gap in episode status visibility: after approval, the episode stays in `APPROVED` status during the entire TTS audio generation process. Users see toasts for "audio generating" and "audio ready" via SSE, but the episode table and detail page only show the static `APPROVED` badge until TTS completes. On page reload, there is no indication that audio generation is in progress.

Separately, all cron schedule evaluation happens in UTC (`Clock.systemUTC()`). Users in timezones like Europe/Amsterdam cannot schedule generation at their local time while automatically accounting for CET/CEST daylight saving transitions.

## Goals / Non-Goals

**Goals:**
- Episode status accurately reflects TTS generation in progress, persisted in the database
- Frontend shows a spinner/indicator for episodes generating audio, both on initial load and via SSE updates
- Podcast cron schedules can be evaluated in a user-configured IANA timezone
- Automatic DST handling via `java.time.ZoneId`

**Non-Goals:**
- Per-user timezone (this is per-podcast, matching the existing per-podcast cron)
- Timezone conversion for display of episode timestamps (those remain as-is)
- Changes to source polling scheduling (remains UTC-based)

## Decisions

### 1. New `GENERATING_AUDIO` enum value in EpisodeStatus

Add `GENERATING_AUDIO` to the existing `EpisodeStatus` enum. The TTS async method sets this status at the start, before calling the TTS provider. On success it transitions to `GENERATED`, on failure to `FAILED`.

**Why not reuse GENERATING?** `GENERATING` represents the LLM pipeline (with `pipelineStage` tracking). TTS generation is a distinct phase with different semantics: the script is already finalized, only audio is being produced. A separate status makes both states unambiguous.

**Why not a boolean flag?** A status enum value is consistent with the existing pattern, works with status filters, and persists naturally in the database.

### 2. Timezone stored as IANA string on podcast entity

Add a `timezone TEXT NOT NULL DEFAULT 'UTC'` column to the `podcasts` table. The value is an IANA timezone identifier (e.g., `Europe/Amsterdam`, `America/New_York`). Java's `ZoneId.of()` handles DST transitions automatically.

**Why per-podcast, not per-user?** The cron schedule is already per-podcast. A user might want different podcasts on different schedules in different timezones.

**Why not store UTC offset?** UTC offsets don't handle DST. IANA identifiers like `Europe/Amsterdam` automatically switch between CET (+01:00) and CEST (+02:00).

### 3. Scheduler timezone evaluation

`BriefingGenerationScheduler` currently uses `Clock.systemUTC()` for all time comparisons. The change modifies it to evaluate each podcast's cron expression in that podcast's configured timezone by converting `now` to the podcast's zone before calling `cronExpression.next()`.

### 4. Frontend timezone selector

A text input with datalist providing common timezone suggestions, rather than a full dropdown of all ~500 IANA timezones. The backend validates the timezone string via `ZoneId.of()`.

## Risks / Trade-offs

- **[Invalid timezone string]** A user could type an invalid timezone. Mitigation: backend validates with `ZoneId.of()` and returns 400 on invalid values.
- **[Stale GENERATING_AUDIO on crash]** If the app crashes during TTS, episodes stuck in `GENERATING_AUDIO` would block new generation. Mitigation: startup cleanup already handles `GENERATING` episodes; extend it to also recover `GENERATING_AUDIO`.
- **[Existing podcasts default to UTC]** All existing podcasts will have `timezone = 'UTC'`, matching current behavior. No migration of existing schedules needed.