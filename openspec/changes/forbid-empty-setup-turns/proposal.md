## Why

In multi-speaker episodes the model sometimes writes a contentless "setup" turn: one speaker announces a point ("One small skeptical flag though.") and the OTHER speaker then supplies the actual substance ("And it's the right one to raise. They published no benchmark scores..."). This leaves a dangling, incoherent handoff — the second speaker agrees with and answers a point the first speaker never actually made. It reads as a logical gap even though it doesn't break the episode.

## What Changes

- Add a shared "NO EMPTY SETUP TURNS" rule (`ComposerUtils.buildNoEmptySetupBlock`): whoever teases or announces a specific point (caveat, flag, question, fact, statistic) must state its substance in the same turn; a handoff is allowed only when the next speaker adds genuinely new information.
- Include the rule in the interview and dialogue compose prompts (monologue/briefing has no handoff, so it is not added there).

## Capabilities

### Modified Capabilities

- `interview-composition`: the prompt forbids contentless setup turns.
- `dialogue-composition`: same.

## Impact

- `src/main/kotlin/com/aisummarypodcast/llm/ComposerUtils.kt` (new `buildNoEmptySetupBlock`)
- `src/main/kotlin/com/aisummarypodcast/llm/{Interview,Dialogue}Composer.kt`
- Affects newly generated episodes only. No data, schema, or API changes.
