---
okf_version: "0.2"
type: rule-rationale
title: The teaser names distinct topics from different parts of the episode
answers: editing the teaser block, or diagnosing a teaser that repeats the opening story
status: stable
source: src/main/kotlin/com/aisummarypodcast/llm/InterviewComposer.kt, TEASER
generated:
  by: claude-opus-5
  at: 2026-09-15T00:00:00Z
---

A teaser is only a reason to keep listening if it points at material the
listener has not reached. Asked simply to preview the episode, the model offers
three angles on the story it has just opened with, which promises nothing: the
listener has already heard it and has no more reason to stay than before.

The rule therefore requires at least three distinct topics drawn from different
parts of the episode, under 40 words, without spoiling the punchlines. Episode
208 named one topic; 209 named three, spread across turns 14, 31 and 42, in 37
words.
