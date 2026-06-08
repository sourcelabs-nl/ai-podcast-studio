## Context

The compose-stage prompts (briefing, dialogue, interview) shared a tone block built by `buildToneBlock()` in `ComposerUtils.kt`: a single sentence ("Go loose and have fun with it. Be playful, crack jokes...") appended to the SIGN-OFF engagement bullet, plus a Friday-only extra sentence. Generated episodes contained no actual humor. Analysis of episodes 132-136 showed three structural causes: the instruction's placement made it read as sign-off-only guidance, its vagueness lost against the prompt's many strict countable rules (which the LLM does follow, e.g. the 4-5 interruptions rule), and the "save the energy" emphasis instruction contradicted it.

This is a retrofit document: the change is already implemented and validated by regenerating episode 132 (Friday) as episode 137, which contains the requested humor beats.

## Goals / Non-Goals

**Goals:**
- Make the humor instruction effective: episodes contain 2-3 genuine, story-tied humor moments.
- Keep the Friday boost working (extra beat, higher energy, end-of-week acknowledgement).
- Remove the contradiction between the emphasis-rationing rule and the playful tone.
- Keep the rule shared across all three composers in one place (`ComposerUtils.kt`).

**Non-Goals:**
- No per-podcast humor configuration (could be a follow-up; `customInstructions` already allows overrides).
- No changes to interruptions, variety picker, or any other engagement rule.
- No API, schema, or frontend changes.

## Decisions

1. **Concrete, countable phrasing over vague encouragement.** The prompt's rules that demonstrably work (interruptions, turn length) are specific and countable. The humor rule now mirrors that pattern: "2-3 genuine moments of humor per episode", "HARD REQUIREMENT", a flavour menu of categories, and joke hygiene rules (1-2 sentences, never explain, skip serious stories). Alternative considered: keeping the prose-style instruction but capitalizing it — rejected because vagueness, not casing, was the core failure.

2. **First engagement bullet, not sign-off suffix.** The block is interpolated immediately after the `Engagement techniques:` header in all three composers so it frames the entire episode. Alternative considered: its own top-level prompt section — rejected to keep the prompt structure stable; the engagement list is where behavioral rules live.

3. **Rename `buildToneBlock()` → `buildHumorBlock()`.** The semantics changed from generic tone to a humor contract; the name should follow. All three call sites updated; no other references existed.

4. **Scope the emphasis rule instead of deleting it.** "Save the energy for what truly stands out" was useful for rationing emphasis but read as a tone damper. Reworded to "Not every story warrants peak emphasis; reserve the strongest emphasis..." with an explicit cross-reference: "This tempers emphasis only, NOT the playful tone from the HUMOR & TONE rule". Alternative considered: deleting the sentence — rejected because emphasis rationing is independently valuable (prevents everything-is-exciting scripts).

5. **Flavour categories are speaker-neutral.** The block is shared by the single-speaker briefing composer, so flavours avoid two-speaker-only constructs (e.g., "tease the other speaker"); banter still emerges in dialogue/interview via the existing interruptions rule.

## Risks / Trade-offs

- [LLM overshoots and forces jokes into every segment] → The block caps at 2-3 moments, requires story-tied humor, forbids generic filler, and excludes serious/negative stories.
- [Humor degrades information density] → Jokes must land in 1-2 sentences and never derail the segment.
- [Friday detection uses server-local `LocalDate.now()`] → Acceptable: server runs in the podcast's timezone (Europe/Amsterdam); pre-existing behavior unchanged.

## Migration Plan

Already deployed: code change, full test suite (864 tests) green, app restarted, episode 132 regenerated as 137 for before/after comparison. Rollback is a single revert of the three composer files and `ComposerUtils.kt`.

## Open Questions

- Should humor intensity become a per-podcast setting (e.g., off / subtle / playful)? Deferred; `customInstructions` can override per podcast today.
