## 1. Store the window on the episode

- [x] 1.1 Add `V67__add_episode_window.sql` with nullable `window_start` and `window_end` on
      `episodes`, and the two fields on the `Episode` entity. Verify with an integration test that
      the columns round-trip and that a row written without them reads as null.
- [x] 1.2 Add `EpisodeRepository.findLatestCoveredWindowEnd`, excluding `FAILED` and `DISCARDED`
      and windows ending after the one being resolved. Verify with integration tests for the
      happy path, for a failed and a discarded episode, and for no windows at all.
- [x] 1.3 Expose `windowStart` and `windowEnd` on `EpisodeResponse`.

## 2. Resolve the window

- [x] 2.1 Add `EpisodeWindow` (half-open, instants) and `EpisodeWindowResolver` with `resolve`,
      `resolveForNow`, `windowOf` and the shared `zoneOf`. Verify with unit tests: a weekday slot
      spans 24 hours, a Monday slot reaches back to Friday, an unparseable cron and timezone fall
      back, `resolveForNow` ends now, and `windowOf` handles a missing and an unparseable window.
- [x] 2.2 Implement the gap extension and its cap. Verify with tests for an extension over a failed
      day, for coverage past the previous slot not shortening the window, and for a five-week gap
      being capped.

## 3. Select on the window

- [x] 3.1 Change `ArticleEligibilityService.findEligibleArticles` to take the window and filter on
      it, comparing instants and keeping an article whose date is absent or unparseable. Remove
      `resolveAgeGateCutoff`. Verify with unit tests for before, after, both boundaries, an offset
      timestamp, a missing date and an unparseable date.
- [x] 3.2 Thread the window through `LlmPipeline.aggregateScoreAndFilter`,
      `PodcastService.generateBriefing`/`generateBriefingAsync`/`runGenerationPipeline` and
      `EpisodeService.createGeneratingEpisode`.
- [x] 3.3 Make a full-pipeline retry and a regeneration use the source episode's stored window,
      falling back to `resolveForNow`. Verify with a test that the retry selects on the stored
      window.

## 4. Wait for the pollers

- [x] 4.1 Add `SourceService.findSourcesBehindWindow`, treating a never-polled source, a failed last
      poll and a poll older than the source's own interval as behind. Verify with unit tests for
      each case plus a disabled source.
- [x] 4.2 Gate `BriefingGenerationScheduler` on that check, with the window ending at the cron slot
      and the deadline measured from it. Verify with tests that a behind source defers without
      creating anything, that generation proceeds once covered, that it proceeds anyway after the
      deadline, and that the window is resolved for the slot rather than for the current time.
- [x] 4.3 Add `app.episode.max-window-days` (7) and `app.episode.poll-coverage-deadline-minutes`
      (30) to `AppProperties` and `application.yaml`.

## 5. Re-run a past window

- [x] 5.1 Add `PodcastService.rerunEpisodeAsync`, creating a fresh episode on the source episode's
      window with `updateLastGenerated = false`, and `EpisodeNotRerunnableException` for an episode
      without a window. Verify with tests for the happy path and the rejection.
- [x] 5.2 Add `POST .../episodes/{episodeId}/rerun`, restricted to `FAILED` and `DISCARDED`, and map
      the new exception to 409 in `PodcastExceptionHandler`.

## 6. Verification

- [x] 6.1 Run `mvn test` and confirm the full suite passes (1381 tests).
- [x] 6.2 Run the code review and fix any violations, then re-review until clean. The first round
      found two violations: the re-run status gate lived only in the controller (moved into
      `rerunEpisodeAsync` as an `EpisodeNotRerunnableException`), and the endpoint had no test
      (four controller tests plus a service test for the gate added). Also acted on two warnings:
      an unbounded cron enumeration (capped at 1000 slots with a WARN) and a missing index on
      `(podcast_id, window_end)`. The re-review is clean.
- [x] 6.3 Restart the app and confirm the migration applies and the API carries the window. V67
      applied cleanly (schema at v67), the app started without errors, `EpisodeResponse` returns
      `windowStart`/`windowEnd` (null for the 205 existing episodes, as designed), and a re-run of
      a GENERATED episode is refused with 409 and code `episode_not_rerunnable`.
- [x] 6.4 On the next scheduled run, confirm the window is recorded on the new episode and that the
      coverage gate logs its decision. Episode 225 carries the window
      [2026-09-18T13:00:00Z, 2026-09-21T14:00:00Z), and `app.log` records it filtering 2113 down to
      191 articles.
