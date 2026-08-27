## Why

Episode 187 burned a five-minute compose and 9.98 cents, then failed at the TTS stage with "Dialogue script produced no speaker turns". The compose LLM had returned a complete, correctly alternating interview with every speaker tag omitted. `RoleTagValidationAdvisor` only looked for tags outside the allowed role set, and an untagged script has no invalid tags, so it read as valid and rode all the way to `DialogueScriptParser`, which found zero turns. Retrying the episode could never help: retry resumes at the failed stage and re-parses the same stored, untagged script.

## What Changes

- `RoleTagValidationAdvisor` treats a multi-speaker response carrying no speaker tag at all as a validation failure, feeding the model a correction and re-issuing the request on the same retry budget it already uses for invalid tags.
- A wrong tag is still reported ahead of a missing one, so a response carrying only `<function_results>` keeps its existing, more specific error.
- The missing-tag check accepts a square-bracketed opener (`[expert]`) and an opener with no matching closer, because both are already recovered downstream and retrying them would waste a compose on a script the pipeline can voice.
- The shared speaker-tag prompt block states that speaker tags are mandatory on every line of spoken text, ahead of the existing rule about which delimiters to use.
- No breaking changes.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `compose-script-cleanup`: the requirement "Invalid speaker tags are rejected before storage with a self-correcting retry" broadens from rejecting only wrong tags to rejecting any multi-speaker response the TTS parser cannot read, which includes a response with no speaker tags at all.

## Impact

- `src/main/kotlin/com/aisummarypodcast/llm/RoleTagValidationAdvisor.kt`: validation returns a problem describing the failure (summary plus model-facing correction) instead of a bare set of invalid roles.
- `src/main/kotlin/com/aisummarypodcast/llm/ComposerUtils.kt`: `buildSpeakerTagFormatBlock` gains the mandatory-tags rule, which reaches both the dialogue and interview prompts.
- `src/test/kotlin/com/aisummarypodcast/llm/RoleTagValidationAdvisorTest.kt`: covers the untagged-script retry, the exhausted-retry failure, and the square-bracket opener that must not be retried.
- The advisor is registered only by `DialogueComposer` and `InterviewComposer`, so briefing (monologue) scripts, which legitimately carry no tags, are unaffected.
- Changing the prompt changes the `CachingChatModel` key, so a previously cached untagged response for an otherwise identical prompt is no longer replayed.
