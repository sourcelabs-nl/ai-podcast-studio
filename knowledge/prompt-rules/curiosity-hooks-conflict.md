---
okf_version: "0.2"
type: rule-rationale
title: CURIOSITY HOOKS may work against the cliffhanger rule
status: stable
source: src/main/kotlin/com/aisummarypodcast/llm/InterviewComposer.kt, CURIOSITY HOOKS
generated:
  by: claude-opus-5
  at: 2026-09-15T00:00:00Z
---

CURIOSITY HOOKS asks the interviewer for rhetorical questions and teaser hooks
*before transitions*. That is precisely the shape STRATEGIC CLIFFHANGERS calls a
topic announcement rather than a cliffhanger, and it is asked for on every
transition rather than once or twice an episode. The suspicion is that it
satisfies the model's sense of having produced a hook, cheaply and often, and so
suppresses the expensive deferred kind.

Measured by [[curiosity-hooks-ablation]], and the suspicion holds in the shape
predicted rather than in size. Removing the rule left the number of genuinely
deferred hooks unchanged at 7 over five runs, while the promises that are
answered immediately fell from 6 to 1. The rule does not suppress real
cliffhangers; it manufactures fake ones beside them. The judged cliffhanger
score is identical either way, so the effect is invisible to the metric.

Related: [[cliffhangers-must-defer]]
