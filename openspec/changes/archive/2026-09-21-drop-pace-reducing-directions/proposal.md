## Why

A listener reported the expert's opening reply in episode 208 as noticeably slow. The cause was `[measured and clear]` on an unanchored first turn, and `unsteer-cold-open` now strips a delivery direction from every speaker's entrance. That fix is scoped to position, which raised the obvious follow-up: what does the same cue cost when the composer writes it in the middle of an episode, where the read is anchored by preceding audio in the right voice?

Measurably more, not less. Turn 30 of episode 208 is a 601-character expert turn. Synthesised four times each against the live API, with that turn's real `synthesisContext`, changing nothing but the cue:

- without the cue: 35.6, 36.3, 36.6, 36.7 seconds (mean 36.3)
- with `[measured and clear]`: 45.6, 45.6, 46.1, 47.9 seconds (mean 46.3)

27% slower, and the two sets of samples do not overlap: the slowest plain read is still nine seconds faster than the quickest cued one. Anchoring does not soften a pace instruction, so position cannot solve this one.

The post-processor already refuses to forward a direction that flattens or distorts a read. A direction that slows a read belongs in the same category: the engine obeys it literally across the whole turn, and the result is a paragraph audibly out of step with the ones around it.

## What Changes

- `InworldScriptPostProcessor` gains `PACE_REDUCING_INSTRUCTION_WORDS` (`measured`, `slow`, `slowly`, `deliberate`, `unhurried`, `languid`, `ponderous`, `plodding`, `sluggish`, `halting`, `drawling` and their variants).
- `flattensDelivery` becomes `degradesDelivery` and tests both word sets, so the name still describes what the function decides.
- The Inworld script guidelines stop listing pace as something a direction may adjust, and name pace-reducing cues alongside `[deadpan]` and `[monotone]` as forbidden.
- The steering example in the guidelines drops its pace clause, becoming `[warm and conversational]`. It is the string the composer imitates most, and it demonstrated the category the rule now forbids.
- Cues that add warmth, energy or brightness are unaffected.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `inworld-script-postprocessing`: the drop rule covers pace-reducing directions as well as flattening ones.
- `inworld-tts`: the script guidelines forbid a pace-reducing direction.

## Impact

- Backend: `InworldScriptPostProcessor` (new word set, `flattensDelivery` renamed to `degradesDelivery`), `InworldTtsProvider` (`CORE_GUIDELINES` steering bullet reworded).
- Tests: `InworldScriptPostProcessorTest` gains 2 cases and extends the whole-word test.
- No schema, API payload shape, frontend, or configuration change.
- Published episodes are unaffected; the change applies to audio generated from now on.
