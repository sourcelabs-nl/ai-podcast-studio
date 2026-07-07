## Why

Episode 163 failed at TTS with `No voice configured for role 'function_results'`: the compose LLM (`z-ai/glm-5.2`) leaked a tool-call artifact (`<function_results>...</function_results>`) into the script alongside the real `<interviewer>`/`<expert>` tags. Nothing between compose and TTS validated that emitted tags were actually the podcast's configured speaker roles, so the bad tag rode all the way to the TTS provider before failing with an opaque error, well after the LLM call that produced it (and its context) was gone.

## What Changes

- Add `RoleTagValidationAdvisor`, a Spring AI `CallAdvisor` wired into `DialogueComposer` and `InterviewComposer`'s compose call via `.advisors(...)`. It inspects every `<role>...</role>` tag in the LLM response against the podcast's allowed roles (`resolveSpeakerRoles(podcast)` for dialogue, the fixed `interviewer`/`expert` pair for interview).
- On an invalid role tag, the advisor appends an LLM-visible error message (naming the bad tag and the allowed tags) to the request and retries in-place, up to 2 retries (3 attempts total) — the same validate/re-prompt/retry pattern as Spring AI's built-in `StructuredOutputValidationAdvisor`, applied to free-text tagged output instead of typed/JSON output (`validateSchema()` only validates JSON, and forcing this project's ~1000-word creative scripts into JSON to use it would risk truncation/escaping failures on long responses from weaker models).
- If the model still produces an invalid tag after exhausting retries, the advisor throws `IllegalStateException` with the offending role(s) — this moves the failure from an opaque TTS-side error to a clear compose-stage error, still surfacing as episode `FAILED` as before.
- `SPEAKER_TURN_PATTERN` (previously private to `ComposerUtils.kt`) is widened to `internal` so the advisor can reuse the existing tag-extraction regex instead of duplicating it. A new `resolveSpeakerRoles(podcast)` helper centralizes the dialogue role-set fallback (`ttsVoices` keys, or `host`/`cohost`) so prompt-building and validation can't drift apart.

## Capabilities

### New Capabilities
(none)

### Modified Capabilities
- `compose-script-cleanup`: adds a new requirement that composed dialogue/interview scripts must use only the podcast's configured speaker roles, with the model given a bounded, LLM-visible chance to self-correct before the pipeline stage fails.

## Impact

- `src/main/kotlin/com/aisummarypodcast/llm/RoleTagValidationAdvisor.kt` (new)
- `src/main/kotlin/com/aisummarypodcast/llm/DialogueComposer.kt`, `InterviewComposer.kt` (wire in the advisor)
- `src/main/kotlin/com/aisummarypodcast/llm/ComposerUtils.kt` (`resolveSpeakerRoles`, widened regex visibility)
- `src/test/kotlin/com/aisummarypodcast/llm/RoleTagValidationAdvisorTest.kt` (new)
- No changes to `DialogueScriptParser`, TTS providers, or the frontend script renderer — they keep consuming the same tagged-text format; this only prevents an invalid tag from ever reaching them.
- Already implemented and merged (commit `3755e35`); this change documents it retroactively per the project's OpenSpec workflow rule.
