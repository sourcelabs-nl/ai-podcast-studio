---
okf_version: "0.2"
type: finding
title: The judge's anchors match the hand audit of episode 209
status: stable
method: one judge call over episode 209's stored script, compared against the by-hand audit recorded in [[episode-208-vs-209]]
model_version: deepseek/deepseek-v4.1-flash
stale_after: 2026-12-15T00:00:00Z
generated:
  by: claude-opus-5
  at: 2026-09-15T00:00:00Z
---

The first live run of `ScriptJudge` was pointed at episode 209, whose attention
devices had already been counted by hand, so the judge could be checked against
something rather than merely inspected.

It agreed on every quantity the audit recorded. Two forward promises, both paid
off, at distances of 37 and 22 turns against the 22 and 37 counted by hand. Ten
humour beats against the roughly ten counted, three of them from the expert,
every one reacting to the turn before. Three teaser topics against three. The
anchors are checkable: the promises sit at turns 12 and 20 and pay off at 49 and
42, and opening the script there shows the audit's own hooks.

This is one episode and is calibration, not proof of accuracy. It says the
judge is worth trusting enough to build a baseline with, not that its anchors
are right on a script nobody has read. A judge that is wrong is worse than none,
because it would justify deleting prompt rules that work, so the check is
repeated on a handful of episodes before any rule is changed on the strength of
a number.

Two operational facts from the same run. The call took 12 seconds and cost under
a cent (3658 input tokens, 381 output), so judging the 166-episode archive is
affordable in one pass. And a second request for the same episode returned the
stored row in 18 milliseconds without reaching the model, which is the
already-scored skip working: re-running over the archive costs nothing for work
already done.

The overall score was 0.93. That number is a convenience over the three
components and its weighting is an equal split chosen because nothing yet says
one device matters more than another. It is not a norm, and
[[length-is-not-a-defect]] applies here too: a figure becomes a threshold only
once a distribution has been read, never because it looks like one.
