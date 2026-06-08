## Why

Listener feedback: the Friday episodes open with a direct "Happy Friday" style shout-out, which feels like too much. The end-of-week acknowledgement should stay, but woven in conversationally instead of as a greeting.

## What Changes

- The Friday extra instruction in `buildHumorBlock()` (`ComposerUtils.kt`) keeps the extra humorous beat and higher energy, but no longer requests an explicit end-of-week acknowledgement in the opening or sign-off.
- It now allows the end of the week to be acknowledged only conversationally and in passing ("It's the end of the week...", "What a week..."), and explicitly forbids direct greetings or shout-outs like "Happy Friday".

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `script-humor`: the "Friday boost" requirement changes — the end-of-week acknowledgement must be conversational and in passing, never a direct "Happy Friday" style greeting or shout-out. (Note: `script-humor` is introduced by the pending change `rework-humor-prompt-block`; this delta applies on top of it.)

## Impact

- `src/main/kotlin/com/aisummarypodcast/llm/ComposerUtils.kt` (`buildHumorBlock()` Friday extra string and its KDoc)
- Affects all three compose-stage prompts (briefing, dialogue, interview) on Fridays only
