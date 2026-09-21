## Why

Episode 207 (11 September 2026) asked for three sounds and got three moods. Its script carried `[laughs]` twice and `[chuckles]` once, none of which is a name Inworld documents. `InworldScriptPostProcessor` treats an unrecognised bracketed tag as a steering instruction, which is the right default for free-form prose but the wrong answer here: instead of a single laugh, each tag asked for the rest of the turn to be *read* laughing, and the provider re-emitted that direction onto every later chunk of the turn.

The composer's intent is not in doubt. The same prompt on a different model (`z-ai/glm-5.3`, episodes 202 and 205) produced correctly spelled `[laugh]` tags, so the plural is a spelling slip rather than a request for a mood. The guidelines already warn that an unrecognised name is read as a delivery instruction, and the model ignored the warning.

## What Changes

- `InworldScriptPostProcessor` gains `SOUND_TAG_ALIASES`, mapping near-misses of the documented sound names onto the documented spelling: plurals and participles of each name (`laughs`, `laughing`, `sighs`, `coughing`, `yawns`), `laughter`, the `chuckle` family, and the `clears throat` and `throat clear` phrasings.
- `normalizeSoundName` consults that map after the exact-match check, so a folded tag becomes a sound everywhere the documented name would be: kept on every model, and never treated as a steering instruction.

The fold is deliberately narrow. It covers only near-misses of the six documented names, so genuine prose directions such as `[warm and conversational]` and `[light]` remain steering instructions and keep their existing handling.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `inworld-script-postprocessing`: the sound-name requirement gains the alias fold.

## Impact

- Backend: `InworldScriptPostProcessor` (new alias map, `normalizeSoundName` rewritten from an expression to a lookup).
- Tests: `InworldScriptPostProcessorTest`, four tests added.
- Already generated episodes are unaffected; the fix applies to audio generated from now on.
