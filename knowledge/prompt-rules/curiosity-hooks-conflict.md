---
okf_version: "0.2"
type: rule-rationale
title: CURIOSITY HOOKS may work against the cliffhanger rule
status: draft
source: src/main/kotlin/com/aisummarypodcast/llm/InterviewComposer.kt, CURIOSITY HOOKS
generated:
  by: claude-opus-5
  at: 2026-09-15T00:00:00Z
---

This is inferred from reading the prompt and auditing one episode. It has not
been measured and carries no confirmation events.

CURIOSITY HOOKS asks the interviewer for rhetorical questions and teaser hooks
*before transitions*. That is precisely the shape STRATEGIC CLIFFHANGERS calls a
topic announcement rather than a cliffhanger, and it is asked for on every
transition rather than once or twice an episode. The suspicion is that it
satisfies the model's sense of having produced a hook, cheaply and often, and so
suppresses the expensive deferred kind.

Testing it means an ablation: remove the rule, hold the episode date and the
variety selection fixed, bypass the LLM cache, and compare across several runs.
Until that is run, this entry is not grounds for changing the prompt.

Related: [[cliffhangers-must-defer]]
