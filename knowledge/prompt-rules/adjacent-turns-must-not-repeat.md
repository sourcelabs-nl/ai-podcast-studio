---
okf_version: "0.2"
type: rule-rationale
title: Two turns by one speaker are fine; two turns saying the same thing are not
answers: tempted to add a structural check that blocks two consecutive turns by one speaker
status: stable
source: src/main/kotlin/com/aisummarypodcast/llm/InterviewComposer.kt, STRATEGIC CLIFFHANGERS
generated:
  by: claude-opus-5
  at: 2026-09-15T00:00:00Z
verified:
  - by: human:s.oudmaijer
    at: 2026-09-15T00:00:00Z
    note: heard in the published episode 209 and identified as the overlap, not the adjacency
---

Episode 209 shipped turns 12 and 13 both on the interviewer: "Let's flip the
lens to coding agents" followed immediately by "Flipping the lens to coding
agents". What makes that sound wrong is the near-verbatim overlap between the
two, not the fact that one speaker held the floor twice. Two consecutive turns
by one speaker are acceptable.

That distinction decides where the fix belongs. The overlap is a writing defect
and is addressed in the prompt: the parking turn of a cliffhanger now hands the
floor back instead of also announcing the next topic, which is what made the
model perform the same transition twice. See [[cliffhangers-must-defer]].

It is not addressed structurally. Merging the two turns was tried and dropped:
it turns two turns saying the same thing into one turn saying it twice, so it
fixes nothing a listener can hear while suppressing an adjacency that is
legitimate. Nothing in the pipeline detects repeated content between adjacent
turns, and nothing should unless it can tell a redundant restatement from a
deliberate one.

Related: [[dedup-guards-articles-not-claims]], for the same question at the
cross-episode grain.
