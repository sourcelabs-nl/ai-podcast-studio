# Design

## Shared rule blocks

`buildStakesBlock()` and `buildSoWhatBlock(questioner)` live in `ComposerUtils.kt`, like the other shared rules, so the wording is defined once. The stakes block goes into all three prompts. The so-what block takes the questioner's name ("the interviewer", "one of the speakers") and goes into interview and dialogue only, because the briefing has one speaker.

## Dropping, not only reordering

Putting stakes first is not enough when an item has no stake. The stakes rule therefore lets such an item shrink to one sentence or go, and the rapid-fire tier follows suit: "up to N" with grouping and dropping allowed. The segment stays mandatory so the structure around it does not change.

## Fewer backchannels, no fixed duration

Backchannels were part of what filled the interviewer's turns without content, so the cap drops to at most 2. The fixed 60-90 second segment length made every story the same size, so the interview and dialogue prompts ask for concise segments with room for the stories that matter. The briefing prompt never had the duration.
