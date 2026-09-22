---
okf_version: "0.2"
type: rule-rationale
title: An initialism stays in capitals
answers: an acronym or model name comes out mispronounced, or reviewing the model-name rewrite
status: stable
source: src/main/kotlin/com/aisummarypodcast/llm/ComposerUtils.kt, buildModelNamesBlock
generated:
  by: claude-opus-5
  at: 2026-09-15T00:00:00Z
verified:
  - by: human:s.oudmaijer
    at: 2026-09-14T00:00:00Z
    note: heard in the published episode 209
---

Model names are rewritten for the engine because it otherwise reads hyphens,
slashes and dots aloud. The rewrite has to preserve one thing: the capitals are
what make the engine spell the letters out. Title-casing an initialism turns it
into a word, which is how `SWE-2` reached the published episode 209 as the
spoken word "swea two" instead of "S-W-E two".

The fix belongs in the spelling, not in a phoneme span or a pronunciation
dictionary: see [[phoneme-spans-under-creative]] for why a span is a hint rather
than an instruction.
