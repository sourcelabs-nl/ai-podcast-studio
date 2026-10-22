# Tasks

## 1. Shared rule

- [x] 1.1 Add `buildDaysAndTimesBlock` to `ComposerUtils` and include it in the briefing, dialogue and interview prompts. Verify with `BriefingComposerTest`, `DialogueComposerTest` and `InterviewComposerTest` `prompt states today's and yesterday's weekday and forbids time-of-day assumptions`.
- [x] 1.2 Yesterday is the calendar day before the episode date. Verify with the two tests in `ComposerUtilsDaysAndTimesTest`.

## 2. Verification

- [x] 2.1 `mvn test` passes (2051 tests).
