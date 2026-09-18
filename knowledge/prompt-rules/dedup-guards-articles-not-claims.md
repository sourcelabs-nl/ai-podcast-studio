---
okf_version: "0.2"
type: finding
title: Dedup guards the article boundary, not the claim boundary
status: stable
method: >
  A listener reported hearing content twice. Both scripts were read directly and
  the shared passage quoted from each, then the articles linked to both episodes
  were compared by id, title and source feed.
generated:
  by: claude-opus-5
  at: 2026-09-17T00:00:00Z
verified:
  - by: human:soudmaijer
    at: 2026-09-17T00:00:00Z
    note: reported the repeat from listening, before it was located in the scripts
  - by: claude-opus-5
    at: 2026-09-17T00:00:00Z
    note: both passages read from the episode scripts, carrying articles confirmed distinct
---

Two consecutive episodes voiced the same statistic, that Claude writes about 80%
of Anthropic's own code, a day apart and both times as fresh information.

The two statements came from different articles: a social post whose whole
subject was that figure, and, the next day, a broader piece about frontier labs
slowing down that cited the same figure as one supporting fact among many. Both
articles were about their own story and neither duplicated the other.

So `TopicDedupFilter` was not wrong. It clusters candidate articles against the
titles of recent episodes' articles and marks each cluster new or a continuation,
and at that grain there was nothing duplicated to find. The repetition only
exists in the finished scripts, where one fact carried by two unrelated stories
got voiced twice.

This is the boundary the existing machinery does not cover. Article-level dedup
catches a story told twice; nothing catches a claim told twice. The compose-stage
`searchPastEpisodes` tool cannot stand in for it either, since it is deliberately
barred from driving coverage decisions after it demoted a lead story on a
spurious keyword match, and the `[FOLLOW-UP: ...]` headers it defers to are
themselves downstream of the article-level clustering.

How often this happens is not known. A first attempt to measure the base rate
across the recent archive returned zero repeats, but it had been reading 404
responses from a wrong endpoint and was discarded; no valid measurement has
replaced it. One confirmed occurrence is not a rate, and nothing here argues for
building a claim-level check before that number exists.

Related: [[echo-turns]], [[adjacent-turns-must-not-repeat]]
