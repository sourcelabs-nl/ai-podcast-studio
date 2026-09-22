---
okf_version: "0.2"
type: experiment
title: Removing CURIOSITY HOOKS cuts the cheap promises, not the real ones
answers: deciding whether to remove or keep the CURIOSITY HOOKS bullet, or wondering why the judged score does not move when a prompt rule does
status: stable
method: >
  Two arms, five composes each, of the same source episode (208) from its own
  linked articles, so the article set, the episode date and the prompt-variety
  selection were identical across all ten runs. The LLM cache was bypassed on
  every run, so each is an independent sample rather than a replay. The arms
  differ in exactly one bullet of the interview prompt, verified by diffing the
  two prompt sources; the NO EMPTY SETUP TURNS rule that shares that string was
  reattached to a neighbouring bullet so it was present in both arms. Judged at
  scorer version 1. The arms ran on separate application starts.
model_version: deepseek/deepseek-v4.1-flash (judge); compose model per podcast config
stale_after: 2026-12-15T00:00:00Z
generated:
  by: claude-opus-5
  at: 2026-09-15T00:00:00Z
---

The question is whether CURIOSITY HOOKS, which asks for a teaser hook before
every transition, suppresses the deferred kind that STRATEGIC CLIFFHANGERS asks
for once or twice an episode. See [[curiosity-hooks-conflict]] for the reasoning
that motivated it.

**The headline score does not move.** Cliffhanger score averaged 0.60 in both
arms.

**The counts underneath it do.**

| | bullet present | bullet removed |
|---|---|---|
| promises made, 5 runs | 13 | 8 |
| of those, deferred | 7 | 7 |
| pooled deferred / promises | 0.54 | 0.88 |
| runs deferring nothing | 1 of 5 | 0 of 5 |
| cliffhanger score range | 0.00 to 1.00 | 0.50 to 1.00 |

The number of genuine deferred hooks is unchanged at 7 in both arms. What the
bullet adds is five extra promises across five runs that are answered
immediately. That is the predicted mechanism: the bullet is satisfied cheaply
and often, and the cheap article is produced alongside the expensive one rather
than instead of it.

**The score cannot see this.** It rewards having one or two deferred hooks and
does not penalise the topic announcements around them, so a metric-only reading
of this experiment would conclude the bullet is neutral. Any future gate on the
cliffhanger score inherits that blindness.

**What is not established.** Five runs per arm, 21 promises in total, so the
ratio is suggestive rather than settled. Humor scored higher with the bullet
removed (0.69 against 0.85) and nothing in the removed text concerns humor,
which is the clearest available evidence that differences of that size in this
data are not reliable. Whether rewording the bullet beats deleting it was not
tested: only removal was run.

**What it supports.** Removal costs nothing measurable and removes the worst
case: the one run that deferred nothing at all was in the arm that kept the
bullet. A prompt change on this basis is defensible; a claim that it improves
episodes is not.
