---
okf_version: "0.2"
type: experiment
title: Episode 208 against 209, an observation rather than an experiment
status: stable
varied: the attention-device rules in the compose prompt
held_fixed: none of the conditions an experiment needs
runs: 1
runs_reaching_model: 1
cache_bypassed: false
generated:
  by: claude-opus-5
  at: 2026-09-15T00:00:00Z
---

Episode 209 was regenerated from a later prompt and audited against 208. Forward
hooks went from 0 to 2, both parked and both paid off 22 and 37 turns later with
a back-reference; humor beats from roughly 3 to roughly 10, with 3 from the
expert and all reactive; teaser topics from 1 to 3 across turns 14, 31 and 42.
Cost was 5 cents to compose and 32 cents for TTS.

This is not a controlled comparison and must not be cited as one. The two
episodes cover different articles, the run count is one, the cache was not
bypassed, and the variety selection was not held: `PromptVarietyPicker` rotates
on podcast and date, so 208 and 209 did not receive the same directives. The
numbers are consistent with the rules working and establish nothing on their
own.

What it does establish is defects, which a single run is enough to show: the
double transition described in [[cliffhangers-must-defer]], the spoken "swea
two" in [[initialisms-stay-capitalised]], and the repeated adjacent turns in
[[adjacent-turns-must-not-repeat]].

A real comparison waits on the scorer: OpenSpec change `script-attention-scoring`.
