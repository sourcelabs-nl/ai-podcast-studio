---
okf_version: "0.2"
type: rule-rationale
title: Speaker alternation is repaired, never enforced
status: stable
source: src/main/kotlin/com/aisummarypodcast/llm/ComposerUtils.kt, mergeConsecutiveSameSpeakerTurns
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
checks only that no tag falls outside the allowed roles and that the response
carries a speaker tag at all. Episode 209 shipped turns 12 and 13 both on the
interviewer, through TTS and out to the feed, with nothing in the log.

What the pipeline now does is merge them, in the clean-up chain alongside the
other repairs, and log at WARN. That guarantees the structural invariant TTS is
written against and makes the model's rule-breaking countable. It does not make
the writing better: two turns saying the same thing become one turn saying it
twice. The prompt remains the only thing that stops them being written.

Merging rather than re-prompting is the same trade the other repairs make: a
compose run costs minutes and a rewrite returns a different script, so a run
wrong in one adjacency and right everywhere else would be discarded to fix the
adjacency.

Proposal and reasoning: OpenSpec change `merge-consecutive-speaker-turns`.

Related: [[cliffhangers-must-defer]]
