## Context

This is a follow-up to `spoken-model-names`. A read-through of episode #135 (interview style) surfaced three listenability patterns. Like the existing audio-readability rules, the fix lives entirely in the shared compose-prompt blocks in `ComposerUtils.kt`, appended verbatim into all three composers. Prompt-only, single module, no data/API/dependency impact.

## Goals / Non-Goals

**Goals:**
- Stop the TTS engine from voicing social handles, package/domain punctuation, and dense research-name stacks.
- Keep each rule a single shared function so it lives in one place.

**Non-Goals:**
- Post-processing or sanitizing already-generated scripts.
- Deterministic per-term overrides (the per-podcast pronunciation map still covers exact cases).
- Editorial story-count/length tuning.

## Decisions

- **Broaden `buildModelNamesBlock()` rather than add a fourth near-duplicate block** for packages/repos/domains, since they share the same "voice it the way you say it" intent. The label changes to "MODEL, PRODUCT & PACKAGE NAMES"; the prompt-assertion tests are updated to match.
- **Two new sibling blocks** (`buildHandlesBlock`, `buildResearchNamesBlock`) follow the established one-rule-per-function pattern (`buildPunctuationBlock`, `buildNumbersBlock`).
- **The handles rule reinforces the IPA-slash discipline**, because the observed failure showed handles wrapped in `/.../` notation that is meant only for pronunciation-guide terms.

## Risks / Trade-offs

- [LLM may strip a handle that was actually the newsworthy subject] → Rule says use the real name when known and only fall back to a generic descriptor; the name/identity is preserved, only the raw handle string is avoided.
- [Research-names rule could over-suppress a codename that IS the news] → Wording explicitly carves out "voice its codename only when the name itself is the news".
- [Broader package/domain rule could rewrite an identifier the listener needs verbatim] → Examples bound the behavior to natural spoken forms; exact strings remain available via the pronunciation map.
