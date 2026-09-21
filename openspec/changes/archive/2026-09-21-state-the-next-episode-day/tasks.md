## 1. Resolving the next episode day

- [x] 1.1 Add `EpisodeWindowResolver.nextEpisodeDateAfter(podcast, episodeDate)` walking the cron forward from the start of `episodeDate` and returning the first slot landing on a later day; verify tests cover a weekday cron mid-week (the next day), a Friday (the following Monday), and a cron firing several times a day (the next distinct day, not the same day again)
- [x] 1.2 Bound the forward search and return null rather than a guess when it finds nothing; verify tests cover an unparseable cron and a cron firing less often than the search horizon, and that both log a warning

## 2. Carrying it to the prompt

- [x] 2.1 Add `nextEpisodeDate` to `ComposeContext`, documented as pipeline-derived like `ttsScriptGuidelines`; verify the project compiles
- [x] 2.2 Fill it in from the resolver at every point where `LlmPipeline` derives the TTS guidelines, so a generation, a retry, a recompose and a preview all carry it; verify `LlmPipelineTest` passes with the resolver stubbed

## 3. The prompt directive

- [x] 3.1 Add `ComposerUtils.buildNextEpisodeBlock` rendering "tomorrow (<weekday>)" for the next day and the weekday name otherwise, localized to the podcast language; verify tests cover both shapes and a non-English locale
- [x] 3.2 Make the directive forbid inventing any other interval and explicitly permit saying nothing; verify a test asserts the forbidden wording is named in the block
- [x] 3.3 Return an empty block when the next day is unknown or is not after the episode's day; verify tests assert the prompt is untouched in both cases
- [x] 3.4 Render the block next to the `SIGN-OFF` directive in the briefing, dialogue and interview composers; verify the project compiles

## 4. Verification

- [x] 4.1 Run `mvn test` and confirm the full suite passes
- [x] 4.2 Restart the app (`./stop.sh` then `./start.sh`), generate an episode on a weekday, and confirm the sign-off names the next working day rather than an invented interval
  - Episode 225, generated Monday 2026-09-21, signs off with "We're back tomorrow, Tuesday."
- [x] 4.3 Run `/code-review --all` and fix violations, repeating until the review is clean
