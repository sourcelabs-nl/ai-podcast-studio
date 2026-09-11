## Why

An episode now owns the article window it covers, so a re-run, a retry and a regeneration all
select the articles of the day they are about. The compose stage never learned that. Every
compose-stage prompt reads the clock instead of the episode:

- The prompt's `Date:` line and its "mention today's date" instruction come from `LocalDate.now()`,
  so a re-run of Wednesday's episode on Friday opens by announcing Friday. The script then states a
  date that contradicts the news it covers.
- The Friday humor beat fires on the day the run happens, so a Wednesday episode re-run on Friday
  asks for end-of-week energy.
- The prompt variety picker is keyed on `(podcastId, episodeDate)` and is documented as a pure
  function of that key, but is handed `LocalDate.now()`. Two different days re-run on the same
  afternoon therefore share their opening, transitions and sign-off shape, which is exactly what the
  rotation exists to prevent.

## What Changes

- **The compose stage is told which day the episode is about.** The episode date is the date its
  article window ends, read in the podcast's own timezone: the window ends at the scheduled slot
  being served, which is the day the episode is published for.
- **The prompt states that date**, the Friday beat follows that date's weekday, and the variety
  picker is keyed on it. A scheduled run is unaffected, since its window ends at the slot it is
  serving now.
- **Every compose entry point carries the date**: a scheduled or manual generation, a full-pipeline
  retry, a retry resuming at compose, a re-run of a past window, and a regeneration. A preview,
  which serves no stored episode, uses the window that ends now.

## Capabilities

### New Capabilities

- `compose-episode-date`: which day a compose-stage prompt is about, how that day is derived from
  the episode's article window, and which run paths carry it.

### Modified Capabilities

- `script-variety`: the `episodeDate` half of the rotation key is defined as the episode's window
  date rather than left implicit.

## Impact

- `EpisodeWindowResolver.episodeDateOf(podcast, window)` derives the date in the podcast's timezone.
- `ComposerUtils`: `buildCurrentDate` becomes `buildEpisodeDate(language, episodeDate)`, and
  `buildHumorBlock(episodeDate)` takes the day whose weekday decides the Friday beat.
- New `ComposeContext` carries the prompt's run-level inputs (TTS guidelines, follow-up annotations,
  topic labels, episode date) through `LlmPipeline.compose` and `recompose` into the three
  composers, replacing the tail of optional arguments they each took.
- `PodcastService` builds that context on every generation, retry, re-run and regeneration path.
- No API, schema or configuration change. Scripts already generated are untouched.
