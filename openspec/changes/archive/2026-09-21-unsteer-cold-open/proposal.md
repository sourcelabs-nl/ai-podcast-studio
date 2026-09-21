## Why

The cold open of episode 180 (17 August 2026) was synthesized as a hushed, intimate story rather than a news hook: the expert's opening turn was `[with quiet awe] Picture someone sitting at home with a single high-end laptop...` and Inworld read it like someone telling a romantic story at a campfire.

The tag itself is legitimate markup that we deliberately allow on `inworld-tts-2`, and it behaves fine everywhere else in the episode. An opening turn is different for a structural reason: it is synthesized with no preceding audio *in that voice* to imitate. `synthesisContext.previousRequests` carries the text of the chunks before it, but on a speaker's first turn all of that text was spoken by someone else, so the engine has no established read of its own to colour and takes the cue as the whole character of the delivery instead. On a podcast configured with `deliveryMode: CREATIVE`, which is the widest emotional range Inworld offers, that overshoot is severe.

The effect is measurable and is not confined to the script's very first turn. Episode 210's expert opened with `[measured and clear]`, and a listener reported the reply as noticeably slow. Synthesising that turn's 474 characters against the live API twice, changing nothing but the cue, produced 26.9 seconds with it and 21.8 seconds without: a 23% drag over the whole turn.

## What Changes

- `InworldScriptPostProcessor` gains `stripLeadingInstruction(text: String): String`, which removes a delivery instruction sitting at the very start of a text and leaves a leading sound tag such as `[laugh]` in place.
- `InworldTtsProvider` applies that strip to the first chunk of each speaker's opening only: the monologue script's first chunk, or the first chunk of each role's first dialogue turn. Every other chunk and every other turn keeps its steering exactly as before.
- The strip runs *after* chunking and instruction re-emission, not before. A monologue's leading instruction governs the whole script through re-emission, so stripping it pre-chunking would silently delete the direction from the entire episode instead of from the one unanchored request.
- The Inworld script guidelines gain a rule telling the composer never to put a delivery direction on a speaker's very first turn, so the common case is fixed at the source and the strip is a backstop.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `inworld-script-postprocessing`: adds `stripLeadingInstruction` and states that the provider applies it to each speaker's opening chunk.
- `inworld-tts`: the steering re-emission requirement gains the unsteered-opening rule, and the script guidelines requirement gains the first-turn ban.

## Impact

- Backend: `InworldScriptPostProcessor` (new function), `InworldTtsProvider` (`prepareChunks` gains an `isSpeakerOpening` parameter, `generateDialogue` tracks which roles have spoken, `CORE_GUIDELINES` gains a bullet).
- Tests: `InworldScriptPostProcessorTest` and `InworldTtsProviderTest`. Two existing tests encoded the old behavior and were updated: `re-emits the steering instruction on later chunks of a monologue` now expects the opening chunk unsteered, and `steering instructions do not leak across dialogue turns` moved its cue onto a role's second turn so it still exercises leakage.
- No API request payload change, no database change, no frontend change.
- Already published episodes are unaffected; the fix applies to audio generated from now on.
