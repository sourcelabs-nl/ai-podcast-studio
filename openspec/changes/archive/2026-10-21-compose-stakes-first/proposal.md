# Proposal

## Why

Episode 299's script read as a list of about 17 paper abstracts. Each topic opened with how something works and never said why a listener should care. The interviewer's turns were mostly bridges ("And the X paper?") or backchannels, so nobody asked what a story meant in practice. Fixed 60-90 second segments and a rapid-fire tier that had to cover every item exactly kept every story the same size, whether it mattered or not.

## What Changes

- A shared STAKES BEFORE MECHANISM rule in all three compose prompts: every topic opens with what changes for the listener, then explains how it works. An item with no consequence statable in one sentence gets one sentence or is left out.
- A shared ASK SO WHAT rule in the interview and dialogue prompts only (not briefing): at least once per full segment a speaker asks what a story means in practice. A bare bridge turn does not count.
- The interviewer is cast as a curious, skeptical colleague. Backchannels go from 2-3 to at most 2 per episode.
- The fixed "roughly 60-90 seconds each" segment length is removed from the interview and dialogue prompts, replaced by room for stories that matter.
- The rapid-fire tier changes from "exactly N items, do not merge, do not skip any" to "up to N items", where items on one theme may share a point and an item without a takeaway may be dropped. The rapid-fire segment itself stays mandatory.

## Capabilities

### New Capabilities

### Modified Capabilities
- `weighted-subtopics`: "Subtopic-aware pacing in composer" (rapid-fire items may be grouped or dropped).
- `interview-composition`: "The interview prompt asks for backchannels" (at most 2); adds "The interviewer is a curious, skeptical colleague".
- `script-audio-readability`: adds "Stakes before mechanism", "Multi-speaker prompts ask so what", "Segment length is not fixed".

## Impact

- `ComposerUtils` (`buildStakesBlock`, `buildSoWhatBlock`), `InterviewComposer`, `DialogueComposer`, `BriefingComposer`, `SubtopicPlan` (prompt text only), no schema or response change.
- Tests: `InterviewComposerTest`, `DialogueComposerTest`, `BriefingComposerTest`, `SubtopicPlanTest`.
