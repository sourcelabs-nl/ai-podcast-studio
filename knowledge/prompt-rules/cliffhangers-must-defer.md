---
okf_version: "0.2"
type: rule-rationale
title: Cliffhangers must defer and park explicitly
status: stable
source: src/main/kotlin/com/aisummarypodcast/llm/InterviewComposer.kt, STRATEGIC CLIFFHANGERS
generated:
  by: claude-opus-5
  at: 2026-09-15T00:00:00Z
verified:
  - by: claude-opus-5
    at: 2026-09-15T00:00:00Z
    note: failure shape observed in episode 208, corrected shape observed in 209
---

Asked only for forward hooks, the model produces topic announcements: it names
something and then covers it in the very next turn. Episode 208 is the failure
shape, with zero devices that survive a turn. The listener gets the words of a
hook with none of the tension, because nothing is ever actually withheld.

The rule therefore specifies the mechanics rather than the intent: name
something from a story you are not about to cover, park it explicitly, cover at
least three other topics, and open the payoff by referring back to the promise.
Episode 209, the first generation under the rule, produced two hooks, both
parked, both paid off 22 and 37 turns later with a back-reference.

The parking turn hands the floor back rather than moving on to a different
topic. Telling it to move on makes the model announce the next topic in the same
turn the next speaker is about to transition into: episode 209 said "Let's flip
the lens to coding agents" and then "Flipping the lens to coding agents" in
consecutive turns. That double transition is what exposed
[[adjacent-turns-must-not-repeat]].

Proposal and reasoning: OpenSpec change `attention-devices-that-defer`.

Related: [[curiosity-hooks-conflict]]
