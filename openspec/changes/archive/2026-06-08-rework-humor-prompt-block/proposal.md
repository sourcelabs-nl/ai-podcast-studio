## Why

Episodes contained essentially no humor despite the compose prompts asking for a playful tone. Investigation of recent episodes (132-136) showed the cause was prompt construction: the tone instruction was a single vague sentence appended to the SIGN-OFF bullet (reading as sign-off-only guidance), it competed with dozens of strict CAPITALIZED structural rules, and the "Not everything is exciting; save the energy" emphasis instruction directly contradicted it. This change is a retrofit of the implemented fix.

## What Changes

- Rename `buildToneBlock()` to `buildHumorBlock()` in `ComposerUtils.kt` and rewrite it as a dedicated `HUMOR & TONE` engagement rule with concrete, countable requirements (mirroring the interruptions rule that the LLM demonstrably follows): 2-3 genuine humor moments per episode, each tied to a specific story, with a flavour menu (absurd/everyday comparison, playful exaggeration, self-deprecating aside, deadpan one-liner), landed in 1-2 sentences, kept away from serious or negative stories.
- Move the humor block from the SIGN-OFF bullet to the FIRST engagement bullet in all three composers (`BriefingComposer`, `DialogueComposer`, `InterviewComposer`) so it frames the whole episode.
- Keep the Friday boost: on Fridays the block requests one extra humorous beat, higher energy, and an end-of-week acknowledgement in the opening or sign-off.
- Soften the conflicting emphasis instruction in all three composers: "Not everything is exciting; save the energy for what truly stands out" becomes "Not every story warrants peak emphasis; reserve the strongest emphasis for the news that truly stands out", with an explicit note that this tempers emphasis only, not the playful tone from the HUMOR & TONE rule.

## Capabilities

### New Capabilities
- `script-humor`: Shared humor-and-tone prompt rule for all compose-stage prompts: placement as first engagement bullet, countable humor requirements, flavour categories, joke hygiene (brief, story-tied, not on serious stories), and the Friday extra beat.

### Modified Capabilities
- `dialogue-composition`: The "Emphasis on important announcements" requirement changes — the emphasis-rationing instruction must be scoped to emphasis only and must not suppress the playful tone established by the humor rule.
- `interview-composition`: Same emphasis requirement change as `dialogue-composition`.

## Impact

- `src/main/kotlin/com/aisummarypodcast/llm/ComposerUtils.kt`: `buildToneBlock()` → `buildHumorBlock()` (rewritten, documented).
- `src/main/kotlin/com/aisummarypodcast/llm/BriefingComposer.kt`, `DialogueComposer.kt`, `InterviewComposer.kt`: humor block moved to first engagement bullet, removed from sign-off line, emphasis instruction reworded.
- No API, database, or configuration changes. No test changes required (no test asserted on the tone text); full suite (864 tests) passes.
