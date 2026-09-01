## Why

A listener heard the expert turn a quarter of the way into episode 194 (1 September 2026) go oddly flat. The cause is in the script: chunk 16 of 65, around 4:03, opens with a delivery direction.

```
[deadpan] They caught visible mistakes, like a button parked in the wrong spot,
but the gains didn't survive statistical correction. And for invisible...
```

Nothing malfunctioned. `InworldScriptPostProcessor` recognises six sound names (`sigh`, `laugh`, `breathe`, `cough`, `clear throat`, `yawn`) and forwards **any other alphabetic bracketed tag** to `inworld-tts-2` as a steering instruction. Inworld obeyed it, and deadpan is precisely flat, expressionless delivery: roughly 25 seconds of the expert sounding broken.

The blast radius was contained — steering is tracked per speaker turn, and in that episode every turn was a single chunk — so the direction expired with the turn rather than flattening the rest of the episode.

What the mechanism lacks is a floor on the vocabulary. Across the last 25 generated episodes, seven carry a delivery direction and every other one adds warmth or energy:

| cue | episode |
|---|---|
| `warm and conversational` | 184, 185 |
| `with barely contained glee` | 190 |
| `bright and quick` | 183 |
| `playful` | 182 |
| `with quiet awe` | 180 |
| **`deadpan`** | **194** |

`[deadpan]` is the only one that instructs *less* expression, and the only one that drew a complaint. As it stands the composer could just as legitimately emit `[monotone]`, `[robotic]` or `[whispering]` and the engine would comply.

## What Changes

- `InworldScriptPostProcessor` gains a set of instruction words that flatten or distort a read — deadpan, monotone, flat, robotic, emotionless, expressionless, lifeless, dull, bored, disinterested, whispering, muttering, mumbling, shouting, screaming, yelling and their close variants. A steering instruction containing any of them SHALL be stripped rather than forwarded, and the drop SHALL be logged so it is visible when it happens.
- The check is word-based, so a phrase such as `[in a deadpan tone]` or `[flat and bored]` is caught, not just the bare word.
- Dropping a cue is deliberately the safe direction: the turn falls back to neutral delivery, which is never wrong. A false positive costs a little colour; a false negative costs an episode. `flat` is included on that basis even though it could in principle appear in a benign phrase.
- The Inworld script guidelines tell the composer that a delivery direction may adjust warmth, energy or pace but must never remove expression or make a turn harder to hear, naming `[deadpan]` as the cue that went wrong. The engine-side strip stays the backstop.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `inworld-script-postprocessing`: bracketed-tag handling gains the suppression rule for flattening instructions.
- `tts-script-profile`: the Inworld guidelines constrain what a delivery direction may ask for.

## Impact

- Backend: `InworldScriptPostProcessor` (word set, `flattensDelivery`, a logger); `InworldTtsProvider.CORE_GUIDELINES` (one guideline line).
- Tests: `InworldScriptPostProcessorTest` gains suppression cases, including episode 194's exact turn.
- Episode 194's audio is regenerated so the published version loses the flat stretch. The post-processor runs at synthesis time on the stored script, so no script edit is needed; regenerating picks up the new rule. It is published to SoundCloud and FTP, and republishing replaces the audio in place.
- No schema, API or frontend change.
