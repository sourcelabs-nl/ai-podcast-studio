## Why

Listeners are happy with how the podcast sounds, but two compose-prompt rules were too timid. The "EXPLAIN FOR NON-EXPERTS" rule only *permitted* elaboration ("it is GOOD to spend a few extra sentences"), so genuinely complex, cutting-edge concepts often went unexplained for non-specialist listeners. Separately, the deep-dive nudge told the model to research only the SINGLE most newsworthy story with "1-2" calls, which the model read conservatively as one call on one story (observed: exactly one `webSearch` per episode), leaving most of the 3-call budget unused.

## What Changes

- Strengthen `buildAudienceBlock()` ("EXPLAIN FOR NON-EXPERTS") from a permissive allowance into a directive: whenever a genuinely complex or unfamiliar concept comes up, explain it in plain language (what it is, how it works at a high level, why it matters) before moving on. No hard cap on the number of explanations: depth (brief, not a lecture) and the "genuinely complex" filter are the limiters, so it scales with the actual episode. Keeps the existing guardrails (conversational, gives advanced listeners a beat to absorb).
- Broaden `buildWebSearchBlock()` (the deep-dive nudge) from "the SINGLE most newsworthy story, 1-2 calls" to "the 2-3 most newsworthy stories, roughly one call each, aiming to spend 2-3 of the 3-call budget across the standout stories." Applies to both the no-subtopics and subtopics variants. The hard per-episode cap (`RESEARCH_TOOL_CAP = 3`) is unchanged; only the prompt guidance changes.
- Applies to scripts composed after the change; already-generated episodes are unaffected.

## Capabilities

### New Capabilities
<!-- none -->

### Modified Capabilities
- `script-audio-readability`: the "EXPLAIN FOR NON-EXPERTS" rule becomes a directive to actively explain complex concepts, with no fixed count.
- `deep-dive-research`: the compose-prompt nudge directs the model to research the 2-3 most newsworthy stories and spend 2-3 of its 3 calls, rather than the single most newsworthy story with 1-2 calls.

## Impact

- Code: `ComposerUtils.kt` (two prompt blocks: `buildAudienceBlock`, `buildWebSearchBlock`).
- Tests: `BriefingComposerTest` deep-dive assertion updated to the new "2-3 most newsworthy" wording; all composer prompt tests pass.
- No API, schema, dependency, or cost-ceiling changes. No breaking changes. Slightly higher expected per-episode research cost/latency (~2-3 Tavily calls instead of ~1, within the existing 3-call cap).
