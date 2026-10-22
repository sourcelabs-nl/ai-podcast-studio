---
okf_version: "0.2"
type: experiment
title: A newsworthiness rubric cuts paper-heavy scoring only together with a threshold of 7
answers: deciding whether to change the relevance-scoring prompt or a podcast's relevance threshold, or asking why the scoring prompt rates listener interest and caps framework papers
status: draft
generated:
  by: claude-sonnet-5-5
  at: 2026-10-21T00:00:00Z
method: >
  On 2026-10-21 the 70 candidates of episode 299 with outcome USED or
  CUT_BY_COMPOSE_CAP (window 2026-10-19T14:00Z to 2026-10-20T14:00Z) were
  rescored directly through OpenRouter, bypassing the application and its LLM
  cache, with temperature 0.3 and reasoning off. Varied: the scoring prompt, once
  as it stood (noise control) and once per rubric variant. Held fixed: the
  candidate set, the model and the temperature. One run per variant. The probe
  prompt abbreviated the newsType definitions in every arm.
model_version: deepseek/deepseek-v4.1-flash-1017 via OpenRouter
stale_after: 2027-01-21T00:00:00Z
---

# A newsworthiness rubric cuts paper-heavy scoring only together with a threshold of 7

Rule rationale: this entry holds why the relevance-scoring prompt rates listener
interest and not topic fit alone. The prompt is `ArticleScoreSummarizer`; the
change is the OpenSpec change `score-newsworthiness`.

## Problem

Episode 299 read as a list of about 17 topics, mostly arXiv paper abstracts.
`relevanceScore` measured only topic fit, so on-topic papers scored as high as
real news and the compose cap of 40 sent nearly all of them to compose.

## Results

Candidates scoring 7 or higher, and how many of those are arXiv papers:

| scoring | >= 7 | of which papers |
|---|---|---|
| stored production scores | 36 | 23 |
| current prompt, rescored (noise control) | 40 | 23 |
| rubric v1 (no release floor, softer paper cap) | 15 | 4 |
| rubric v2 (shipped) | 20 | 6 |

Rubric v1 overshot: it scored the framework paper "Agentic Interpretation" 9 and
the release Drex Max 6. Rubric v2 added the release floor of 7 and the harder
paper cap: Drex Max 7, Agentic Interpretation 6, the refactoring-ripple study 6.
News items rose above 7 under v2 (DeepSeek open-sourced inference engine,
Anthropic OSS Scanner).

The noise control differs from the stored scores by 4 candidates above 7 with the
same prompt, so differences of that size between arms are not evidence.

## Finding

The rubric alone does not reduce volume while the relevance threshold is 5 and
the compose cap is 40: 22 to 27 candidates sit at 6 and still qualify. Volume
falls only with the threshold at 7, so the threshold of the podcast "The Daily
Agentic AI Podcast" was raised from 5 to 7 with the prompt (a per-podcast
setting).

Cost of all probe runs: $0.08.

## Open

- No script-quality measurement yet: whether episodes built from the rescored
  set read better is not measured (see [[judge-calibration-episode-209]] for how
  far a judge score can be trusted). The first episode built from a
  rescored set is described in [[compose-stakes-first-episode-302]].
- Thin news days with a threshold of 7 are untested; fewer than the compose cap
  may qualify.
- One run per variant on one day's candidates.
