## Why

The TTS engine mispronounces AI model and product names that contain hyphens, abbreviations, or version numbers. In a recent episode "Microsoft MAI-Code-1-Flash" was read incorrectly (hyphens spoken aloud, letter clusters spelled out) instead of as "Microsoft May Code One Flash". Because this is an AI-news podcast, new hyphenated/abbreviated model names appear constantly, so a per-term fix does not scale.

## What Changes

- Add a shared compose-stage prompt rule (`buildModelNamesBlock()` in `ComposerUtils.kt`) instructing the LLM to write AI model and product names the way a person says them aloud: replace hyphens with spaces, speak version numbers and standalone digits as words, and expand letter-clusters to how they actually sound (e.g. "MAI-Code-1-Flash" → "May Code One Flash", "GPT-4o" → "GPT four oh").
- Wire the new rule into all three composers (`BriefingComposer`, `DialogueComposer`, `InterviewComposer`) alongside the existing `buildPunctuationBlock()` / `buildNumbersBlock()` audio-readability rules.
- This applies to every podcast automatically and affects only scripts composed after the change; already-generated episodes are unaffected. For names where the spoken form must be exact, the existing per-podcast pronunciation map remains the deterministic override.

## Capabilities

### New Capabilities
<!-- none -->

### Modified Capabilities
- `llm-processing`: The compose-stage prompt gains a requirement that all three composers instruct the LLM to render AI model and product names in spoken form for the ear.

## Impact

- Code: `ComposerUtils.kt` (new function), `BriefingComposer.kt`, `DialogueComposer.kt`, `InterviewComposer.kt` (wire the block into the prompt).
- Tests: composer prompt tests assert the new block is present in the built prompt.
- No API, schema, or dependency changes. No breaking changes.