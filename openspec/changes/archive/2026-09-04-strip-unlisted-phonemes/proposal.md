## Why

Episode 199's second speaker introduced himself as `/stɛfan/`, and Inworld read the invented transcription out as a mispronounced name: "And I'm /stɛfan/ ... I mean, Stephan, sitting in for the usual voice of reason."

The podcast's pronunciation dictionary holds exactly one entry, `Jarno → /jɑrnoː/`. There is no entry for Stephan, and the plain spelling reads correctly. The model minted the phonemes itself, by analogy with the one entry it was given.

Prompting is already as firm as it can usefully be. The guide says "CRITICAL: ONLY use IPA notation for the exact terms listed below. Do NOT invent or add IPA pronunciation for ANY other words. If a word is not in the list below, write it normally", and a separate composer rule adds that "the IPA slash notation is reserved exclusively for the listed pronunciation-guide terms". The model broke both. A rule that is already stated twice and still violated needs enforcing in code, not restating.

## What Changes

- `TtsScriptSanitizer` takes the podcast's pronunciation dictionary and removes any slash-delimited IPA span that is not one of its values, on the one path every provider and the preview both already go through. The intended word cannot be recovered from a phoneme string, so the span is dropped rather than guessed at; the surrounding sentence stands.
- The span pattern requires at least one non-ASCII character and no internal whitespace, so ordinary prose survives: `and/or`, `TCP/IP`, `input/output` and `24/7` are untouched.
- The capability's placeholder Purpose is replaced with a real one.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `tts-script-sanitization`: gains removal of unlisted IPA spans alongside the existing dash handling.

## Impact

- Backend: `TtsScriptSanitizer.sanitize` takes the dictionary; `TtsRequest.forPodcast` and `PreviewAudioService.billableCharacters` pass it.
- Both TTS entry points already funnel through this one function, so the episode and the preview stay identical, which is what the preview is for.
- No schema, API or frontend change.
