## Context

Em-dashes (`—`, U+2014) and en-dashes (`–`, U+2013) in generated podcast scripts are mishandled by TTS providers: Inworld and OpenAI either read the character literally or break sentence pacing. The dashes enter scripts via two paths: (a) the LLM mirrors the heavy em-dash usage of the composer prompts, and (b) the sponsor-message template in `ComposerUtils` hard-codes an em-dash. The fix must work across all TTS providers (Inworld, ElevenLabs, ElevenLabs Dialogue, OpenAI) without provider-specific code.

## Goals / Non-Goals

**Goals:**
- Guarantee no em-dash or en-dash characters reach any TTS provider, regardless of LLM output.
- Reduce the rate at which the LLM generates em-dashes in the first place, by cleaning prompts.
- Keep the persisted `scriptText` in the database faithful to the LLM output (sanitization is a TTS-input concern, not a storage concern), so the UI can still render the original script for review.

**Non-Goals:**
- Broader Unicode normalization (smart quotes, ellipsis, etc.) - out of scope.
- Provider-specific cleanup (markdown stripping, emoji removal) - still owned by `InworldScriptPostProcessor`.
- Retroactively rewriting historical episodes.

## Decisions

**Decision 1: Sanitize at `TtsPipeline.callProvider`, not at composer output.**
- Rationale: `callProvider` is the single funnel for every TTS provider. Sanitizing here covers all providers with one change and keeps the stored `scriptText` (used by the frontend script viewer) unchanged.
- Alternative considered: sanitize inside each provider. Rejected: four duplicate call sites, easy to forget when adding a new provider.
- Alternative considered: sanitize at compose time and persist the sanitized text. Rejected: loses the original LLM output and conflates two concerns (display vs. TTS input).

**Decision 2: Replace em-dash and en-dash with `", "` (comma + space).**
- Rationale: a comma produces a natural short pause in every TTS engine, matching how a human reader interprets an em-dash break. Stripping outright would jam two clauses together; replacing with a period would over-segment intonation.
- Edge case: consecutive whitespace around the dash is collapsed to a single space after replacement, so `"foo — bar"` becomes `"foo, bar"` not `"foo ,  bar"`.

**Decision 3: Clean composer prompts in addition to sanitizing.**
- Rationale: the sanitizer is a safety net, but cleaner prompts reduce the chance of other dash-like artifacts and shorten the gap between what the user sees in the script viewer and what gets spoken.

**Decision 4: New capability `tts-script-sanitization`, not an extension of `inworld-script-postprocessing`.**
- Rationale: the Inworld post-processor is intentionally provider-specific (handles Inworld's `[laugh]` tags, asterisk emphasis). The new sanitizer is provider-agnostic. Mixing them would muddy both contracts.

## Risks / Trade-offs

- [Risk] A sentence ending in an em-dash followed by another sentence becomes `"foo,. Next sentence"`. → Mitigation: after replacement, collapse `, .` / `, !` / `, ?` patterns back to the original terminator (small post-pass).
- [Risk] The stored script still shows em-dashes in the UI, so users see a mismatch between the viewer and what they hear. → Accepted: the script viewer is for review of LLM output; the audio is the product.