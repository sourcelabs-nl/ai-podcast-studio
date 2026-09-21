## Why

A listener reported that episodes do not hold attention, and named two devices they wanted: jokes that land part-way through, and cliffhangers. Both were already in the prompt, so the question was whether the composer delivers them. Auditing episode 208 against its own rules:

| Rule | Asked for | Delivered |
| --- | --- | --- |
| MID-ROLL CALLBACKS | at least 1 | 2, explicit, back to the cold open |
| HUMOR & TONE | 2-3 genuine beats | ~3, but one is a bare `[laugh]` with no punchline and one a thin one-liner |
| TEASER | preview the most interesting topics | only the opening story |
| STRATEGIC CLIFFHANGERS | 2-3 forward hooks | 0 |

The cliffhanger rule reads "teasing something from a later story before transitioning". The model resolves that ambiguity the easy way: it puts the tease immediately before moving to that same story, so the promise is paid off in the next turn. Every candidate hook in episode 208 works like this, which makes them topic announcements. Nothing in the rule requires distance between promise and payoff.

The humor rule is met on count and still reads as one person being funny at another: every beat in episode 208 is the interviewer's, and each is an aside rather than a reaction. The rule's flavour menu lists shapes of line (comparison, exaggeration, self-deprecating aside, deadpan one-liner), so it produces delivered lines.

Two long-form two-host reference shows were analysed for comparison. Both use very few genuine forward hooks, two or three per 80-105 minutes, reserved for the biggest threads, explicitly parked and paid off 20-30 minutes and several topics later with a spoken callback. Their humour runs the other way round from ours: teasing the co-host, self-deprecation, and reacting to the item just described, with each laugh growing out of the line before it. Their opening preview names five topics; ours names one.

## What Changes

- STRATEGIC CLIFFHANGERS asks for 1-2 forward hooks rather than 2-3, and defines one: it names something specific from a story not about to be covered, parks it, and is paid off only after at least 3 other topics, with the payoff referring back to the promise. A tease the next turn resolves is named as not counting.
- The shared `buildHumorBlock` gains a `multiSpeaker` flag. Where there are two speakers, at least one beat must come from a speaker other than the one who opens the episode, and at least one must be a direct reaction to what the other speaker just said. `BriefingComposer` passes false, having one voice.
- The introduction TEASER must name at least 3 distinct topics from different parts of the episode, and its budget rises from 25 to 40 words, which 3 topics do not fit in.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `interview-composition`: gains a deferred-payoff rule for cliffhangers and a multi-topic rule for the introduction teaser.
- `script-humor`: the shared block requires humor from more than one speaker, and at least one beat that reacts.

## Impact

- Backend: `InterviewComposer` (cliffhanger and teaser bullets), `ComposerUtils.buildHumorBlock` (new `multiSpeaker` parameter), `DialogueComposer` and `BriefingComposer` (call sites).
- Tests: `InterviewComposerTest` gains 3 cases; two existing cases pinned the old wording and were updated.
- No schema, API, frontend, or configuration change.
- Whether these rules actually change the output is not established by this change. The composer is not deterministic, so a single regeneration cannot attribute a difference to the prompt. The three rules were written to be countable so that a later evaluation harness can measure them across several runs and against the existing script archive.
