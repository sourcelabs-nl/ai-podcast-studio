# Design

## One shared block with the episode date

`buildDaysAndTimesBlock(episodeDate)` lives in `ComposerUtils.kt` next to the other shared rules and goes into all three prompts, so the wording is defined once. It formats today's and yesterday's weekday in English regardless of the podcast language, because the rule text is English. Yesterday is the calendar day before the episode date (Sunday on a Monday), not the previous working day.

## Forbid the assumption, not the anecdote

The time-of-day rule targets what the model cannot know: when the listener presses play. "Today" and "yesterday" replace "this morning" for when news broke. First-person anecdotes stay welcome, provided the day they happen on fits the rule.
