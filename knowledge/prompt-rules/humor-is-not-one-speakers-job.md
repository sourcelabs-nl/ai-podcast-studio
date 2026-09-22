---
okf_version: "0.2"
type: rule-rationale
title: Humor is not one speaker's job
answers: editing the humour block, or noticing every joke comes from one speaker
status: stable
source: src/main/kotlin/com/aisummarypodcast/llm/ComposerUtils.kt, buildHumorBlock
generated:
  by: claude-opus-5
  at: 2026-09-15T00:00:00Z
---

Left unspecified, the model assigns humor to the interviewer and leaves the
expert straight, which reads as one person performing at another rather than two
people enjoying a conversation. The Dutch two-host shows we take as reference do
the opposite: the laughs are reactive and both hosts produce them, each landing
on what the other just said.

So the rule asks for beats from both speakers and for them to react rather than
arrive prepared. Episode 209 carried roughly ten beats against 208's three, with
three from the expert, all reactive.

`[laugh]` ownership is a usable proxy for the distribution but not a count of
humor: a joke that carries no laugh tag is invisible to it.

Background: archived change `2026-06-08-rework-humor-prompt-block`.

Related: [[dutch-two-host-shows]]
