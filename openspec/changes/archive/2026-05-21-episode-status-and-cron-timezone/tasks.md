## 1. Backend: GENERATING_AUDIO status

- [x] 1.1 Add `GENERATING_AUDIO` to `EpisodeStatus` enum
- [x] 1.2 Update `EpisodeService.generateAudioAsync()` to set status to `GENERATING_AUDIO` before calling TTS, and publish `episode.audio.started` after the status change
- [x] 1.3 Update `EpisodeService.hasActiveEpisode()` to include `GENERATING_AUDIO` in the active statuses list
- [x] 1.4 Update `EpisodeController.discard()` to reject `GENERATING_AUDIO` episodes with HTTP 409
- [x] 1.5 Update startup cleanup in `EpisodeService` to also recover stale `GENERATING_AUDIO` episodes (mark as FAILED)
- [x] 1.6 Update tests for EpisodeService and EpisodeController to cover the new status

## 2. Backend: Podcast timezone

- [x] 2.1 Create Flyway migration to add `timezone TEXT NOT NULL DEFAULT 'UTC'` column to `podcasts` table
- [x] 2.2 Add `timezone: String = "UTC"` field to `Podcast` entity
- [x] 2.3 Add `timezone: String?` to `UpdatePodcastRequest` and `CreatePodcastRequest` in PodcastController, wire through PodcastService
- [x] 2.4 Add timezone validation in PodcastService (validate with `ZoneId.of()`, throw on invalid)
- [x] 2.5 Update `BriefingGenerationScheduler` to evaluate cron in the podcast's timezone instead of UTC
- [x] 2.6 Update tests for BriefingGenerationScheduler and PodcastController/Service to cover timezone behavior

## 3. Frontend: GENERATING_AUDIO status display

- [x] 3.1 Add `GENERATING_AUDIO` to `STATUSES` array and `STATUS_VARIANT` map in podcast list page
- [x] 3.2 Add GENERATING_AUDIO row rendering in episode table (spinner + "Generating audio..." text, no action buttons, visual indicator)
- [x] 3.3 Add `GENERATING_AUDIO` to `STATUSES` array and `STATUS_VARIANT` map in episode detail page
- [x] 3.4 Update episode detail page to show no action buttons for `GENERATING_AUDIO` status

## 4. Frontend: Podcast timezone

- [x] 4.1 Add `timezone` field to `Podcast` TypeScript interface in `types.ts`
- [x] 4.2 Add timezone input with datalist to the General tab in podcast settings page
- [x] 4.3 Update podcast list page header to show timezone after cron description (when non-UTC)
- [x] 4.4 Update countdown timer to use podcast's timezone for `cron-parser` evaluation instead of hardcoded UTC

## 5. Verify

- [x] 5.1 Run `mvn test` to verify all backend tests pass
- [x] 5.2 Manually verify: approve an episode and confirm GENERATING_AUDIO appears in the table and detail page
- [x] 5.3 Manually verify: set timezone on a podcast and confirm cron countdown reflects the timezone