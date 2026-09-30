---
okf_version: "0.2"
type: finding
title: Scoring a source costs cents a month, so pruning sources is about noise and not about money
answers: deciding whether to disable or narrow a source, or whether a source's LLM cost justifies removing it
status: draft
generated:
  by: claude-sonnet-5-5
  at: 2026-09-30T00:00:00Z
method: >
  Read-only queries against the application database over articles published in
  the 30 days to 2026-09-30 and scored by the filter stage, joined to
  `episode_articles` for use in an episode. Cost is each article's recorded
  `llm_reported_cost_usd`, falling back to `llm_cost_cents`, summed per source.
  The join to `episode_articles` repeats an article once per episode it appears
  in, so a cost summed after that join overstates it; cost is summed before it.
  One 30 day window, one podcast, 18 enabled sources with scored articles.
model_version: deepseek/deepseek-v4.1-flash via OpenRouter (scoring stage)
stale_after: 2026-12-30T00:00:00Z
---

# Scoring a source costs cents a month, so pruning sources is about noise and not about money

All scoring in the window cost about $1.80. The three sources that cost most:

| source | scored | used in an episode | scored 8 or more | scoring cost |
|---|---|---|---|---|
| X - Tooling & builders | 2,533 | 100 | 5% | ~$0.50 (28% of the total) |
| Hackernews frontpage | 419 | 44 | 13% | ~$0.28 (16%) |
| X - Lab people | 511 | 51 | 11% | ~$0.10 (6%) |

Even the most expensive source costs about fifty cents a month, so a source's
LLM cost is not a reason to remove it. The reason to remove one is that it puts
articles in front of the scorer, the dedup stage and the composer that never
reach an episode.

Yield is the figure to judge a source by. In the window, Google AI RSS (16
scored, 0 at score 8 or more) and Bun (2 scored) placed no article in any
episode, while the noisiest source by share, X - Tooling & builders, still
placed 100. A low share of high-scoring articles is therefore not enough to
remove a source: a large feed with a 5% hit rate can still supply more episode
material than a small clean one. The measure is articles used, against the work
the source creates.

Small feeds (under ten scored articles a month) say nothing about a source: one
article either way swings every percentage.

Related: a gate in front of scoring would save about $0.35 a month at this
volume, see [[jev-decisions-endpoint]].
