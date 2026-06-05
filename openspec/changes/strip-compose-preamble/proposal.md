## Why

The compose LLM "thinks out loud" after its last tool call (e.g. "I have enough context. Writing the script now.") before emitting the actual script, despite prompt instructions forbidding text outside speaker tags. This preamble was stored verbatim in `scriptText` on every recent episode: it shows in the dashboard script viewer, pollutes word counts, and for the briefing (monologue) style it would be read aloud by TTS since untagged scripts go to TTS verbatim. (For dialogue/interview styles the TTS parser already discards text outside tags, so audio was unaffected.)

## What Changes

- Add `stripOutsideSpeakerTags()` to `ComposerUtils.kt`: removes any text before the first opening speaker tag and after the last closing speaker tag. Applied in `DialogueComposer` and `InterviewComposer` after topic-order extraction. Untagged scripts pass through unchanged; text between turns is preserved.
- Add `stripLeadingMetaCommentary()` to `ComposerUtils.kt`: for monologue scripts (no tags), conservatively drops the FIRST paragraph only when it is short (≤300 chars) and matches known "I'm about to write" phrasings (writing/draft/compose + script, "enough/plenty of context", "what I need"). Applied in `BriefingComposer`.
- Add `ScriptCleanupTest` covering preamble removal, trailing-text removal, mixed-tag endings, between-turn text preservation, and false-positive protection for genuine spoken openings.

## Capabilities

### New Capabilities
- `compose-script-cleanup`: Post-composition script cleanup that guarantees the stored episode script contains only the script itself, no leaked LLM meta-commentary.

### Modified Capabilities

(none — dialogue/interview specs require tags not be stripped, which is preserved; this only removes text *outside* the tagged span)

## Impact

- `src/main/kotlin/com/aisummarypodcast/llm/ComposerUtils.kt`: two new functions.
- `src/main/kotlin/com/aisummarypodcast/llm/BriefingComposer.kt`, `DialogueComposer.kt`, `InterviewComposer.kt`: one-line cleanup call each in the composition result.
- `src/test/kotlin/com/aisummarypodcast/llm/ScriptCleanupTest.kt`: new (10 tests).
- No API, database, or configuration changes. Existing stored episodes are not rewritten.
