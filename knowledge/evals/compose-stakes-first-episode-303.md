---
okf_version: "0.2"
type: experiment
title: Episode 303 keeps the stakes-first strengths, but its rapid-fire repeated four of six stories and stacked numbers
answers: judging how the stakes-first compose rules held up on a second episode, diagnosing a rapid-fire segment that retells stories already told, or a script that stacks numbers despite the numbers-for-the-ear rule
status: draft
varied: nothing, one observed episode
held_fixed: not applicable
runs: 1
runs_reaching_model: not recorded
cache_bypassed: not recorded
generated:
  by: claude-sonnet-5-5
  at: 2026-10-22T00:00:00Z
---

# Episode 303, the second script composed with the stakes-first rules

Observation of one episode, not a controlled comparison. It follows
[[compose-stakes-first-episode-302]] under the same compose rules and the same
relevance threshold of 7. The fix for the main defect is OpenSpec change
`subtopic-plan-by-topic`; it has not been run on a live episode, so whether it
works in practice is untested.

## Conditions

Episode 303 was generated on 2026-10-22 from 21 articles, 5 of them arXiv papers,
and came out at 2401 words and 886 seconds. One run; no cache bypass was
recorded. The window, the articles and the scoring differ from episode 302, so
the two are not comparable beyond what each shows on its own.

## What worked

- Stakes before mechanism on the lead story, the PHP vulnerability discovery.
- One paper, the merge-gate paper, told as a finding a listener could retell, with
  a so-what and a later callback.
- Interviewer pushback on the claims, as in 302.

## What failed

- **Rapid-fire repeated stories.** Four of the six rapid-fire items retold a story
  already told: the PHP cluster three more times and Claude Duet twice. The dedup
  stage had grouped these articles into topic clusters correctly. `SubtopicPlan`
  ignored the grouping and bucketed per article. The three PHP articles were all
  classified "Agent research" (weight 3, rapid-fire) while the model opened with
  the story, so each became its own rapid-fire item. Claude Duet was split between
  rapid-fire and a full segment.
- **Number stacking** despite the NUMBERS FOR THE EAR rule: 786, 657, 71 and 263
  in one passage; Flint with 40B, 2.5B, 23GB, 48GB and 125 tokens per second; one
  DeepSeek run with six numbers. The rule is in the prompt and the model did not
  follow it, so a longer reminder is unlikely to be the fix.
- **Assumed days and times**: "before you've had coffee" and "not a normal
  Tuesday" (episode 303 aired on a Thursday). Episode 303 was composed before the
  days-and-times rule existed; see [[days-and-times]].

## Related

The repetition here is within one episode. Repeats across episodes are covered by
[[dedup-guards-articles-not-claims]] and [[history-accuracy-audit-2026-09]].

## Open

- The number-stacking defect has no fix yet and no measure of how often it
  occurs across the archive.
- Whether grouping by topic removes the repeats on a live episode is untested.
- No judged score exists for 303; the listener verdict is pending, as for 302.
