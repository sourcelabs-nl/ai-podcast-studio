---
okf_version: "0.2"
type: experiment
title: Episode 302, the first script composed with the stakes-first rules, drops the arXiv papers and asks so-what questions
answers: judging whether the stakes-first compose rules and the newsworthiness rubric made episodes less boring and technical, or asking why renaming subtopics sent scored articles into "Other"
status: draft
varied: the compose prompt, the scoring rubric, the threshold and the subtopic names, all at once
held_fixed: nothing
runs: 1
runs_reaching_model: not recorded
cache_bypassed: not recorded
generated:
  by: claude-sonnet-5-5
  at: 2026-10-21T00:00:00Z
---

# Episode 302, the first script composed with the stakes-first rules

Observation of one episode, not a controlled comparison. The rules are the shared
stakes and so-what blocks in the compose prompts (OpenSpec change
`compose-stakes-first`); the scoring side is in
[[newsworthiness-rubric-episode-299]] (OpenSpec change `score-newsworthiness`).

## Conditions

Episode 302 covers the window 2026-10-20T14:00Z to 2026-10-21T14:00Z and was
generated on 2026-10-21. Its 297 window articles were rescored first with the
newsworthiness rubric, the relevance threshold was 7, the subtopic names were new
and the compose prompt carried the stakes-first rules. Nothing was held fixed
against the earlier episodes: the window, the scoring prompt, the threshold, the
subtopic names and the compose prompt all differ. One run; no cache bypass was
recorded.

## Result

| episode | window | articles used | arXiv papers | script |
|---|---|---|---|---|
| 299 | 2026-10-19 to 20 | 40 | about 20 | not measured here |
| 301 | same window as 302, before rescoring | 35 | 14 | 1320 words, 14 articles in "Other" |
| 302 | 2026-10-20 to 21 | 21 | 0 | 1788 words, 656 seconds |

Observed in the script of episode 302:

- An explicit through-line question ("what actually got cheaper?").
- Interviewer so-what questions and pushback ("party trick or real shift?", "why
  should an agent builder care", a skeptical turn on voice agents).
- Stakes stated before mechanism.
- The rapid-fire segment grouped by theme.

One flaw: the interviewer reacts to "not sandboxed" one turn before the expert
says it.

## Subtopic renames orphan scored articles

Episode 301's 14 articles in "Other" came from renaming subtopics on
2026-10-21. A scored article keeps the subtopic it was classified with, so one
filed under a name that no longer exists counts as the synthetic "Other" bucket of
weight 1. Among them were model releases and coding tools. They fell into the
rapid-fire tier, and the script came out at 1320 words with one full segment.
Rescoring the window's articles restored the classification for episode 302.
Reclassifying automatically on a rename was considered and rejected; the settings
page warns instead (OpenSpec change `subtopic-rename-warning`).

## Open

- No judged score for "boring" or "technical" exists, so whether 302 reads better
  than 299 is a listener verdict that is still pending.
- Which of the changes (rubric, threshold, subtopic names, compose rules) produced
  each difference is not separable from one episode.
- The reaction placed one turn before its cause is a single occurrence.

The next episode under the same rules is [[compose-stakes-first-episode-303]].
