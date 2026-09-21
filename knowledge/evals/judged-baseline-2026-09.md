---
okf_version: "0.2"
type: experiment
title: The judged baseline over the archive
status: stable
method: >
  One judge call per script over every scorable episode in the archive, at scorer
  version 1, in ADVISE mode. 162 episodes scored; the remainder carry no script
  to judge. Single pass, no repetitions, so this is the distribution of one
  sample per episode and says nothing about the spread within an episode.
model_version: deepseek/deepseek-v4.1-flash
stale_after: 2027-09-15T00:00:00Z
generated:
  by: claude-opus-5
  at: 2026-09-15T00:00:00Z
---

What the judge finds across the whole archive, against which any prompt change
is now measurable. Every figure is derived in Kotlin from turn anchors; none of
it was asked of the model. See [[judge-calibration-episode-209]] for the check
that the anchors match a hand audit.

**The three components, each in `[0, 1]`.**

| | min | p25 | median | p75 | max | mean |
|---|---|---|---|---|---|---|
| overall | 0.00 | 0.33 | 0.53 | 0.71 | 0.98 | 0.49 |
| cliffhangers | 0.00 | 0.00 | 0.50 | 0.50 | 1.00 | 0.39 |
| humor | 0.00 | 0.08 | 0.50 | 0.63 | 0.96 | 0.40 |
| teaser | 0.00 | 0.33 | 1.00 | 1.00 | 1.00 | 0.68 |

**Humour is one speaker's job in most of the archive.** Speaker balance has a
median of 0.00 against a ceiling of 0.5, and 91 of 162 episodes have every beat
from a single speaker. That is the failure [[humor-is-not-one-speakers-job]] was
written for, and the archive predates it, so this is the number that rule has to
move. Beats themselves are not scarce: median 4, mean 5.15, and 31 episodes have
none at all.

**Cliffhangers are the weakest device and the most bimodal.** 70 of 162 episodes
defer nothing, while the median episode that defers at all defers by 14 turns.
Promises are made (median 1, up to 14 in one episode) and mostly paid off, with
only 17 episodes leaving any promise unpaid, so the defect is the distance, not
the honesty: the model promises and answers in the next breath. This is the
reading episode 208 suggested, now measured across the archive.

**The teaser is the device that mostly works.** Its median score is 1.00, though
69 episodes still name fewer than the three topics the rule asks for.

**Cost.** 542,777 input and 36,323 output tokens for the whole archive, reported
as under a cent per episode and rounding to zero cents in total at the configured
rates.

**What this does not establish.** One sample per episode cannot separate a
prompt's effect from the composer's own variance, and the archive spans many
prompt revisions, so the spread here mixes change over time with change between
episodes. Setting `app.eval.judge.norm` from the median would gate on a figure
this bundle cannot yet defend; an ablation that repeats one variant under
[[evaluation-run-cache-bypass]] is what makes a difference attributable.
