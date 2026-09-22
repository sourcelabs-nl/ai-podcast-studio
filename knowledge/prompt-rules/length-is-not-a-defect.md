---
okf_version: "0.2"
type: rule-rationale
title: Turn length and episode length are judged by ear, not by the numbers
answers: deciding whether to gate on episode length or turn length
status: stable
source: src/main/kotlin/com/aisummarypodcast/llm/InterviewComposer.kt, STRICT TURN LENGTH
generated:
  by: claude-opus-5
  at: 2026-09-15T00:00:00Z
verified:
  - by: human:s.oudmaijer
    at: 2026-09-15T00:00:00Z
    note: stated directly after reading the structural baseline
---

The baseline showed the 4-sentence cap exceeded in most episodes and episode
length ranging from 11.1 to 18.9 minutes. Neither is treated as a defect. A turn
that runs slightly long is fine as long as it still sounds natural, and episode
length may follow whatever there is to say that day.

That is a decision about what good sounds like, and it settles how the numbers
are used: `turnsOverSentenceCap` and episode duration are descriptive, not
thresholds, and neither becomes a gate. The cap stays in the prompt as pressure
against monologue, not as a limit to enforce. The case that still matters is the
extreme: episode 197 with eleven over-cap turns and an expert median of 104 words
against a corpus median of 60.

The length spread is not driven by how much news there is. `app.compose.
max-articles` is 40 and nearly every episode reaches it, so the 1667 to 2965 word
range occurs at a constant article count. It is the composer varying, not the
input.

See [[structure-baseline-2026-09]] for the measurements.
