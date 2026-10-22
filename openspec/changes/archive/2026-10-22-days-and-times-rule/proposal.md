# Proposal

## Why

The compose prompts only stated the episode's own date, so the model filled in the rest itself. Episode 303 (Thursday 2026-10-22) opened "That is not a normal Tuesday feeling" about the day before. A scan of 98 archived scripts found 10 that assumed the listener's time of day ("Good morning", "this morning", "tonight"), which is wrong for anyone listening later (episodes 217, 219, 223, 241, 258, 268, 282, 289 and 293, among others). Wrong-weekday use is rare (303, and an idiom in 282).

## What Changes

- A shared DAYS AND TIMES rule in all three compose prompts: it states today's and yesterday's weekday, allows a weekday only for those two or a day the articles state, forbids weekdays as figures of speech, and forbids assuming the listener's time of day, both as greeting and for when news broke. First-person anecdotes stay welcome.

## Capabilities

### New Capabilities

### Modified Capabilities
- `script-audio-readability`: adds "Days and times are stated, not assumed".

## Impact

- `ComposerUtils` (`buildDaysAndTimesBlock`), `BriefingComposer`, `DialogueComposer`, `InterviewComposer` (prompt text only), no schema or response change.
- Tests: `ComposerUtilsDaysAndTimesTest`, `BriefingComposerTest`, `DialogueComposerTest`, `InterviewComposerTest`.
