## Context

Compose-stage LLM responses begin with a short meta-commentary paragraph ("I have what I need. Now writing the script.") emitted after the model's final tool call. `TopicOrderExtractor` only strips the trailing topic-order metadata block, so the preamble was stored in `scriptText`. Retrofit document: the fix is implemented and verified live (episode 138 regenerated from episode 132's articles starts cleanly at the first speaker tag).

## Goals / Non-Goals

**Goals:**
- Stored episode scripts contain only the script, never LLM meta-commentary.
- Zero risk of dropping genuine script content.

**Non-Goals:**
- Rewriting existing stored episodes (historical scripts keep their preamble; harmless since dialogue/interview TTS ignores it).
- Changing the TTS-side parser (`DialogueScriptParser` keeps its defensive ignore-and-warn behavior).

## Decisions

1. **Clean at compose time, not parse time.** The cleanup runs in the composers before the script is stored, so the database, dashboard, and word counts are all correct. The TTS parser's existing tolerance stays as a second line of defense. Alternative considered: cleaning in `DialogueScriptParser` only — rejected because the polluted script would still be stored and displayed.

2. **Tagged styles: span-trim via per-turn matches.** `stripOutsideSpeakerTags()` finds all `<tag>...</tag>` turns (non-greedy, backreference-matched) and keeps the substring from the first turn's start to the last turn's end. This removes leading/trailing junk while preserving text between turns and supporting scripts that end on a different tag than they start with. A single greedy span regex was rejected: it would cut a trailing turn whose tag differs from the first tag.

3. **Briefing style: conservative first-paragraph heuristic.** Monologues have no tags to anchor on, so `stripLeadingMetaCommentary()` only drops the first paragraph when it is ≤300 chars AND matches known preamble phrasings. False positives (a genuine opening mentioning "script") are guarded by the length cap and the keyword combinations; false negatives just mean the preamble stays, which is the pre-fix status quo.

## Risks / Trade-offs

- [Novel preamble phrasing not matched by the heuristic (briefing only)] → Preamble remains, same as before the fix; extend the regex when observed.
- [Script legitimately ends with prose outside tags (tagged styles)] → The prompt forbids it and such text was already discarded by the TTS parser; trimming it is strictly more correct.

## Migration Plan

Already deployed: code + 10 unit tests, full suite (874) green, app restarted, verified via live regeneration (episode 138). Rollback: revert four files.

## Open Questions

None.
