---
okf_version: "0.2"
type: experiment
title: Structural baseline over the 18 most recent episodes
status: stable
varied: nothing
held_fixed: not applicable, this is a description of the archive rather than a comparison
runs: 18
runs_reaching_model: 0
cache_bypassed: not applicable, no model call
generated:
  by: claude-opus-5
  at: 2026-09-15T00:00:00Z
verified:
  - by: claude-opus-5
    at: 2026-09-15T00:00:00Z
    note: computed from the stored scripts, deterministic and reproducible
---

Episodes 190 to 209, 28 August to 14 September 2026, read through the metrics
API. This is a description, not a comparison: the episodes cover different
articles and several prompt versions.

| | min | median | max |
|---|---|---|---|
| words | 1667 | 2094 | 2965 |
| minutes | 11.1 | 14.3 | 18.9 |
| turns | 29 | 55 | 73 |
| expert median words per turn | 42 | 60 | 104 |
| expert word share | 65% | 70% | 80% |
| turns over the 4-sentence cap | 0 | 1 | 11 |
| backchannel-shaped turns | 0 | 4 | 7 |
| same-speaker runs | 0 | 0 | 5 |

Four things it settles.

**The length spread is real and is a word-count spread.** 11.1 to 18.9 minutes
at identical settings. Speech rate is stable at roughly 155 words a minute
across the whole range, so duration is words and nothing else. The configured
target of 1500 words governs nothing: every episode overshoots it by 11 to 98
percent.

**The interviewer is quieter than the prompt asks.** The rule asks for a 65/35
split and the expert takes 65 to 80 percent, median 70. No episode is under the
asked-for expert share and several are far over.

**The turn length cap is broken regularly, sometimes badly.** Median one turn an
episode over four sentences, but episode 197 has eleven, with an expert median
of 104 words a turn against a corpus median of 60. That is the monologue failure
the rule exists to prevent, and it happened without anything noticing.

**Backchannel-shaped turns already existed before the rule.** A median of four an
episode across the archive. So tomorrow's episode cannot be judged by whether
candidates appear; the claim needs a delta against this baseline and a read of
the text, since the shape does not distinguish a token of listening from a short
question. See [[backchannels]].

Same-speaker runs are rare (median zero) but not absent, which matches
[[adjacent-turns-must-not-repeat]]: nothing prevents them.

Laugh tags run 0 to 3 an episode and are frequently zero, against roughly ten
humor beats counted by hand in episode 209. They are a weak proxy, as
[[humor-is-not-one-speakers-job]] says, and counting humor needs the judge.
