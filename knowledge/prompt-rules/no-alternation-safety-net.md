---
okf_version: "0.2"
type: finding
title: Nothing enforces speaker alternation
status: stable
method: read of RoleTagValidationAdvisor against an episode that violated the rule
generated:
  by: claude-opus-5
  at: 2026-09-15T00:00:00Z
verified:
  - by: claude-opus-5
    at: 2026-09-15T00:00:00Z
    note: two consecutive interviewer turns reached published audio in episode 209
---

The compose prompt calls two consecutive turns by the same speaker "ABSOLUTELY
FORBIDDEN", which reads like a guarantee. It is not one. `RoleTagValidationAdvisor`
checks only two things: that no tag falls outside the allowed roles, and that
the response carries a speaker tag at all. Alternation is not checked anywhere
in the pipeline, by the advisor or after it.

Episode 209 shipped turns 12 and 13 both on the interviewer, through TTS and out
to the feed, with nothing raising it. The prompt is the only thing holding this
rule, and a prompt is not a validator.

Closing it is a change of its own, not a prompt edit: either extend the advisor,
which already re-issues the request, or repair the alternation after the fact.

Related: [[cliffhangers-must-defer]]
