## Why

An episode's article window is currently derived at run time from the last published episode's
`generated_at`, so no run owns the period it covers. Three failures follow from that.

- **A day cannot be re-run.** A retry or a re-run recomputes the window from the current state, not
  from the state the episode was built on, so yesterday's episode can never be reproduced.
- **A failed day's content lands in the next episode.** Because the cutoff is "since the last
  publication", the articles of a day whose episode failed silently roll into the following day
  instead of belonging to their own day.
- **An episode can be composed before the pollers have caught up.** Generation only checks that
  *some* poll round ran in the last 10 minutes, never that the sources actually fetched the period
  being composed. After the machine has been offline for hours, the episode goes out with a gap: the
  content arrives minutes later and is then attributed to the next day.

## What Changes

- **An episode records the article window it covers** (`window_start`, `window_end`) and that window
  is the run's input from then on. A retry, a re-run and a regeneration all select from the stored
  window rather than recomputing one.
- **The window runs from the podcast's previous scheduled slot to the slot being served.** A daily
  weekday cron yields 24 hours on Tuesday through Friday and reaches back across the weekend on
  Monday, derived from the cron rather than from hardcoded weekdays. The reach is capped by
  `app.episode.max-window-days`, and it extends back to where coverage actually ended when an
  earlier episode failed or was discarded, so no day's content is orphaned between two windows.
- **Generation waits for the pollers to cover the window.** While any enabled source has no
  successful poll covering the window's end, generation is deferred and re-checked on the next tick.
  The wait is bounded by `app.episode.poll-coverage-deadline-minutes`, after which the episode is
  generated anyway and the sources still behind are named at WARN.
- **A past day can be re-run.** `POST /users/{userId}/podcasts/{podcastId}/episodes/{episodeId}/rerun`
  runs the stored window of a failed or discarded episode as a fresh episode.
- **BREAKING** (behaviour, not API): article selection no longer reaches back to the last
  publication. An article older than the window is not picked up by a later episode; the window
  extension covers the gap a failed episode leaves, and a re-run covers a day that has to be redone
  deliberately.

## Capabilities

### New Capabilities

- `episode-article-window`: an episode owns the article window it covers, how that window is
  computed, and how a past window is re-run.

### Modified Capabilities

- `article-eligibility`: the age gate becomes a window filter on the run's own window.
- `podcast-pipeline`: generation resolves and passes the window, and defers while the pollers are
  behind it.
- `pipeline-retry`: a full-pipeline retry reselects from the episode's stored window.

## Impact

- New migration `V67__add_episode_window.sql`: two nullable columns on `episodes`. Existing episodes
  read as having no window and are never treated as having covered a period.
- New `EpisodeWindow` and `EpisodeWindowResolver` in `com.aisummarypodcast.podcast`.
- `ArticleEligibilityService.findEligibleArticles` takes the window; `resolveAgeGateCutoff` is gone.
- `LlmPipeline.aggregateScoreAndFilter`, `PodcastService.generateBriefing` and
  `EpisodeService.createGeneratingEpisode` take the window.
- `BriefingGenerationScheduler` resolves the window from the cron slot and gates on poll coverage;
  `SourceService.findSourcesBehindWindow` answers whether a source is behind.
- New config: `app.episode.max-window-days` (7) and `app.episode.poll-coverage-deadline-minutes` (30).
- `EpisodeResponse` gains `windowStart` and `windowEnd`.
- Reproducibility is still bounded by retention: `app.source.max-article-age-days` (90) prunes
  unused articles and unlinked posts, so a window older than that can no longer be reproduced in
  full.
