## Why

The latest Agentic AI Podcast episode contained em-dash characters (`—`) in the generated script, which TTS engines do not pronounce correctly (they either read the character literally or stumble on the pacing). Em-dashes leak into scripts because the composer prompts themselves are full of em-dashes and the LLM mirrors that style, and because the sponsor message template hard-codes an em-dash.

## What Changes

- Add a provider-agnostic `TtsScriptSanitizer` that replaces em-dash (`—`) and en-dash (`–`) characters with a comma and space (`", "`) immediately before the script is handed to any TTS provider.
- Wire the sanitizer into `TtsPipeline.callProvider` so it runs for every provider (Inworld, ElevenLabs, OpenAI, ElevenLabs Dialogue).
- Strip em-dashes from the composer prompt templates (`BriefingComposer`, `DialogueComposer`, `InterviewComposer`, `ComposerUtils` sponsor message) and add an explicit "do not use em-dashes or en-dashes" instruction to each prompt.

## Capabilities

### New Capabilities
- `tts-script-sanitization`: Provider-agnostic sanitization step that runs on every script before TTS submission. Initial scope: replace em-dashes and en-dashes with a comma. The capability is intentionally separate from `inworld-script-postprocessing`, which is Inworld-specific.

### Modified Capabilities
None. The composer prompt changes are wording-only and do not alter any spec-level requirements of `dialogue-composition`, `interview-composition`, or `llm-processing`.

## Impact

- Code: `src/main/kotlin/com/aisummarypodcast/tts/TtsPipeline.kt`, new `TtsScriptSanitizer.kt`, prompt strings in `src/main/kotlin/com/aisummarypodcast/llm/{BriefingComposer,DialogueComposer,InterviewComposer,ComposerUtils}.kt`.
- Tests: new unit tests for `TtsScriptSanitizer`; existing TTS pipeline tests unaffected (sanitization is a pass-through for dash-free scripts).
- No database migration, no API change, no breaking change.
