## Why

The hook/cold-open is often written as one long, comma-stacked run-on sentence ("Picture this. It's the middle of the night, and somewhere a pair of engineers..."). The TTS engine reads such dense narration at a noticeably faster clip than the short conversational turns that follow, so the intro sounds rushed/sped-up relative to the rest of the episode (measured ~185 wpm in the opening vs ~168 wpm episode average for the 2026-06-08 episode). It is not a sample-rate or speed-setting bug — the whole file is 48 kHz with a constant speaking rate — it is the model's prosody on long flowing sentences.

## What Changes

- Add a shared "OPENING PACING" rule (`ComposerUtils.buildColdOpenPacingBlock`) instructing the model to write the hook/cold-open as a few short sentences with full-stop (and occasional ellipsis) pacing beats, rather than one long run-on, so the opening's spoken pace stays even with the rest of the episode.
- Append this rule to the HOOK OPENING directive in the briefing, dialogue, and interview compose prompts.

## Capabilities

### Modified Capabilities

- `interview-composition`: the hook-opening prompt also requires short, paced opening sentences.
- `dialogue-composition`: same.

## Impact

- `src/main/kotlin/com/aisummarypodcast/llm/ComposerUtils.kt` (new `buildColdOpenPacingBlock`)
- `src/main/kotlin/com/aisummarypodcast/llm/{Interview,Dialogue,Briefing}Composer.kt`
- Affects newly generated episodes only. No data, schema, or API changes.
