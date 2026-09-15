## Why

Handing the floor back with a short reaction is wanted, but the model sometimes writes that reaction as a bare repeat of the words it just heard. Episode 219 closed its introduction with the interviewer saying "Let's get into it." and the expert's entire next turn was "Let's." That spends a speaker switch on nothing: no content, no energy, no new information. Because each turn is synthesized on its own, it also lands as two stranded syllables in the audio, right at the top of the episode where the listener is deciding whether to stay.

The existing BACKCHANNELS rule sanctions short turns, but only as a token of pure listening dropped in while the expert is mid-explanation. It does not say that a short turn must use its own words, so an echo of the previous line slips through as if it were a backchannel.

## What Changes

- Add a shared "NO ECHO TURNS" rule (`ComposerUtils.buildNoEchoTurnBlock`): a turn SHALL never consist only of words echoed back from the line it follows, and outside the BACKCHANNELS device every turn is at least one complete sentence carrying its own content or its own energy ("Let's do this!" rather than "Let's.").
- Include the rule in the interview and dialogue compose prompts (monologue/briefing has no handoff, so it is not added there).

## Capabilities

### Modified Capabilities

- `interview-composition`: the prompt forbids turns that only echo the previous speaker, and sets a floor on short reaction turns.
- `dialogue-composition`: same.

## Impact

- `src/main/kotlin/com/aisummarypodcast/llm/ComposerUtils.kt` (new `buildNoEchoTurnBlock`)
- `src/main/kotlin/com/aisummarypodcast/llm/{Interview,Dialogue}Composer.kt`
- Affects newly generated episodes only. No data, schema, or API changes.
