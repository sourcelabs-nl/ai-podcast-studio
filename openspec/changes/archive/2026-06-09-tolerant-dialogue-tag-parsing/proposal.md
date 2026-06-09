## Why

For dialogue and interview podcasts, the LLM occasionally emits malformed speaker tags despite explicit prompt rules: a turn opened with `<expert>` closed with `</interviewer>`, or a closing tag missing entirely before the next opening tag. `DialogueScriptParser` matched turns with `<(\w+)>(.*?)</\1>`, which requires the closing tag to match the opening tag. Any mismatched turn failed to match and its spoken text was silently logged as "outside speaker tags" and dropped from the rendered script and the generated audio. This produced episodes with missing dialogue turns.

## What Changes

- Make `DialogueScriptParser` (backend, TTS) tolerant of malformed tags: a turn's role is taken from its opening tag, and the turn ends at the next tag token regardless of whether the closing tag matches (or is present at all).
- Mirror the same tolerant logic in the frontend script viewer (`parseMultiSpeakerScript`), which used the identical brittle `<(\w+)>...</\1>` regex and therefore dropped the same turns when rendering chat bubbles.
- Stray text that appears outside any opening tag is still ignored with a warning (unchanged behavior).
- The compose-stage prompt rule forbidding mismatched/consecutive tags is retained as a first line of defense; the parsers are now the robust backstop.
- One-time data repair: episode 141 (the only affected episode) had its stored `script_text` normalized to clean matching tags (38 turns recovered from a mangled 31) and its audio regenerated.

## Capabilities

### Modified Capabilities
- `tts-provider-abstraction`: dialogue script parsing now recovers turns with mismatched or missing closing tags instead of dropping them.

## Impact

- **Modified**: `DialogueScriptParser.kt` (tolerant parsing), `DialogueScriptParserTest.kt` (new malformed-tag scenarios), `frontend/src/components/script-viewer.tsx` (tolerant `parseMultiSpeakerScript`).
- **Data**: episode 141 `script_text` normalized in place (backup retained); audio regenerated.
- No schema, API, or dependency changes.
