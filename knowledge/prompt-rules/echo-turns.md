---
okf_version: "0.2"
type: rule-rationale
title: A short turn has to bring its own words
status: stable
source: src/main/kotlin/com/aisummarypodcast/llm/ComposerUtils.kt, buildNoEchoTurnBlock
generated:
  by: claude-opus-5
  at: 2026-09-15T00:00:00Z
verified:
  - by: human:s.oudmaijer
    at: 2026-09-15T00:00:00Z
    note: heard in episode 219 and identified as too few words rather than as a turn that should not exist
---

Episode 219 ended its introduction with the interviewer on "Let's get into it."
and the expert's entire next turn reading "Let's." Passing the floor back there
is fine. What fails is the size of what comes back: two syllables, lifted from
the line they answer, so the exchange costs a speaker switch and returns
nothing. "Let's do this!" would have been the same beat done properly.

The gap it slipped through is the backchannel device. That rule sanctions a turn
of a few words, so a short turn no longer looks wrong on its own, and it said
nothing about where the words come from. See [[backchannels]] for the device and
its two conditions, neither of which this turn met: it came from the expert
rather than the listener, and nobody was mid-explanation.

The rule therefore forbids the echo rather than the brevity, and carves out the
backchannel explicitly, so "Right." and "Exactly." mid-explanation survive. This
is the second repetition defect found by ear rather than by any check: the first
was an overlap between two turns by one speaker, and the shape here is an echo
of the other speaker. See [[adjacent-turns-must-not-repeat]] for why neither is
detected structurally.

`ScriptMetrics.backchannelCandidates` had already flagged the turn, at index 3,
one word. Nothing surfaced it until the dashboard grew a tab that does, which is
what the finding actually cost.

Proposal and reasoning: OpenSpec change `forbid-echo-turns`.
