## Why

The episode table and detail page do not reflect that TTS audio generation is in progress after approval. The status stays at APPROVED until audio is ready, so on page reload there is no visual indicator of ongoing work. Additionally, podcast cron schedules are evaluated in UTC only, making it impossible to schedule generation at the correct local time (e.g., 06:00 CET/CEST in Europe/Amsterdam with automatic DST handling).

## What Changes

- Add a `GENERATING_AUDIO` episode status between `APPROVED` and `GENERATED`, set when TTS begins and cleared when TTS completes or fails
- Backend sets `GENERATING_AUDIO` in the database so the state persists across page loads
- Startup cleanup recovers stale `GENERATING_AUDIO` episodes (mark as FAILED)
- Frontend shows a spinner with "Generating audio..." for episodes in `GENERATING_AUDIO` status, in both the episode table and detail page
- Add a `timezone` field to the podcast entity (IANA timezone string, default `UTC`)
- Scheduler evaluates cron expressions in the podcast's configured timezone, with automatic DST handling via `java.time.ZoneId`
- Frontend settings page gets a timezone selector next to the cron field
- Frontend countdown timer uses the podcast's timezone for next-run calculation

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `episode-review`: Add `GENERATING_AUDIO` to the episode status lifecycle, between APPROVED and GENERATED
- `podcast-pipeline`: Include `GENERATING_AUDIO` in active episode check; evaluate cron in podcast's timezone instead of UTC
- `podcast-management`: Add `timezone` field to podcast entity and CRUD operations
- `frontend-dashboard`: Display `GENERATING_AUDIO` status with spinner in episode table and detail page; use podcast timezone for countdown
- `frontend-event-notifications`: Add toast for `episode.audio.started` status transition
- `frontend-podcast-settings`: Add timezone selector to the General tab

## Impact

- **Backend**: EpisodeStatus enum, EpisodeService, TtsPipeline, BriefingGenerationScheduler, EpisodeController, Podcast entity, PodcastController, PodcastService
- **Database**: New migration adding `timezone` column to `podcasts` table
- **Frontend**: Episode list page, episode detail page, settings page, types, event context
- **No breaking API changes**: timezone defaults to UTC, GENERATING_AUDIO is additive