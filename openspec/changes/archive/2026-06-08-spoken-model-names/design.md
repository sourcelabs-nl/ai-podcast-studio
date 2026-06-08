## Context

Compose-stage prompts already carry shared audio-readability rules built in `ComposerUtils.kt` (`buildPunctuationBlock()`, `buildNumbersBlock()`, `buildAudienceBlock()`), each appended verbatim into the prompts of all three composers. The TTS engine mishandles hyphenated/abbreviated AI model names. This change adds one more shared rule following the established pattern. It is a single-module, prompt-only change with no data-model, API, or dependency impact.

## Goals / Non-Goals

**Goals:**
- Instruct the LLM to write AI model and product names in spoken form for the ear, automatically for every podcast.
- Reuse the existing shared-block pattern so the rule lives in exactly one place.

**Non-Goals:**
- Deterministic per-name pronunciation (the existing per-podcast pronunciation map already covers exact overrides).
- Post-processing or rewriting already-generated scripts/episodes.
- Any change to TTS providers or the synthesis path.

## Decisions

- **Add `buildModelNamesBlock(): String` to `ComposerUtils.kt`** and append `${buildModelNamesBlock()}` after `${buildNumbersBlock()}` in `BriefingComposer`, `DialogueComposer`, and `InterviewComposer`.
  - *Alternative considered:* embed only in the Inworld `scriptGuidelines()`. Rejected: the rule is provider-agnostic and belongs with the other compose-stage audio rules, matching the existing pattern.
  - *Alternative considered:* per-podcast pronunciation entries only. Rejected by the user: does not scale to the constant stream of new model names.
- **Wording gives concrete examples** ("MAI-Code-1-Flash" → "May Code One Flash", "GPT-4o" → "GPT four oh") so the LLM has a clear pattern to generalize from.

## Risks / Trade-offs

- [LLM may guess a spoken form wrong for an ambiguous name] → For names that must be exact, the per-podcast pronunciation map remains the deterministic override; the general rule handles the common case.
- [Over-application could rewrite names that were already fine] → Wording targets names with hyphens/abbreviations/version numbers and pairs with the existing "only use IPA for listed terms" discipline; examples bound the behavior.