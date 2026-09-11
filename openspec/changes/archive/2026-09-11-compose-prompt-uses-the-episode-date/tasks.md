## 1. Derive the episode's day

- [x] 1.1 Add `EpisodeWindowResolver.episodeDateOf(podcast, window)`, reading the window's end in
      the podcast's timezone via the existing `zoneOf`. Verify with unit tests for a weekday slot
      and for a window whose end falls on different dates in UTC and in the podcast's timezone.

## 2. Make the prompt read the episode's day

- [x] 2.1 Replace `buildCurrentDate(language)` with `buildEpisodeDate(language, episodeDate)` and
      give `buildHumorBlock(episodeDate)` the day whose weekday decides the Friday beat.
- [x] 2.2 Add `ComposeContext` (TTS guidelines, follow-up annotations, topic labels, episode date)
      and take it on `compose` and `buildPrompt` of `BriefingComposer`, `DialogueComposer` and
      `InterviewComposer` in place of their tail of optional arguments, keying
      `varietyPicker.pick` on the context's date. Verify with unit tests that the prompt states the
      episode's date and not today's, that the Friday beat follows the episode's weekday, and that
      the variety selection is the one of the episode date.

## 3. Carry the day through every run path

- [x] 3.1 Take the context on `LlmPipeline.compose` and `recompose`, fill in the TTS guidelines
      resolved from the podcast's provider, and derive the date from the resolved window in `run`
      and `preview`.
- [x] 3.2 Pass it from every `PodcastService` path: the generation pipeline, both retry resume
      points (resolving the episode's own window for the compose resume), and the regeneration.
      Verify with a test that a regeneration recomposes with the source episode's window date.
- [x] 3.3 Run `mvn test` and confirm the suite is green.
