---
okf_version: "0.2"
type: finding
title: Jev as a script judge, measured against this project's own judge
status: stable
generated:
  by: claude-opus-5
  at: 2026-09-21T00:00:00Z
method: >
  Two runs of `score` questions against the live decisions endpoint over the 19
  archived episodes that carry a stored EpisodeScore, read through the REST API.
  (1) One four-level rubric per script for attention as a whole. (2) Three
  four-level rubrics per script, one per component, whose top level names the
  amount AttentionScoring's own target uses. Compared by Spearman rank
  correlation against the stored score the current judge produced for the same
  script. Cost and latency read from each response's own usage block. The
  LangChain figures quoted below are that team's, not measured here.
model_version: typesafe/jev-1.13-20260917
stale_after: 2026-12-21T00:00:00Z
---

# Jev as a script judge, measured against this project's own judge

LangChain published a comparison of Jev against LLM judges for agent evaluation
("Jev-as-a-Judge for Agent Evals", Daniel Shea and Seán Roche, 2026-09-20,
reproduced at https://github.com/danielgshea/jev-as-a-judge). Their result: over
five weather-agent examples repeated 100 times, Jev matched a human oracle on
500 of 500 binary decisions against 99.8% for GPT-5.6 Terra, 96.4% for Luna and
80.0% for Claude Sonnet 4.6, with per-case score variance 92-913x lower than the
three, at 0.44s and $0.00035 per call. They state the caveat themselves: five
examples, one agent, one domain.

Their latency agrees with what this project measures, 0.44s against 450-500ms
over 120 calls in [[jev-decisions-endpoint]] and 588ms over the runs here. Their
per-call cost is roughly ten times this project's $0.00004, which is the state
size rather than a disagreement: they send an agent trace, the dedup gate sends
short summaries, and these script runs sent 4,073 input tokens for $0.000176.
Price tracks the input, as that entry already records.

## The variance argument does not transfer, because `ScriptJudge` does not score

The property LangChain measures is repeatability of a number a model emits.
`ScriptJudge` never emits a number. It is asked only for positions (which turn
makes a promise, which turn pays it off, which turns are funny), and every
number is computed from those indices by `AttentionScoring`. A model asked to
count over a long document gets the count wrong invisibly, while a model asked
to point at a turn can be checked by opening the script there. So the low-
variance property is already held here, by construction rather than by model
choice, and adopting Jev would trade a checkable answer for an unauditable one.

Jev cannot replace it in any case: it answers `noul`, `score` and `choice`, and
none of them returns a turn index.

## Where Jev agrees with the current judge, and where it does not

Over the 19 scored episodes, Spearman against the stored component scores:

| component | Spearman | what the question asks for |
|---|---|---|
| teaser | **+0.62** | are distinct subjects named in the opening |
| humor | +0.29 | how many jokes, spread over which speakers |
| cliffhangers | +0.22 | how far a payoff sits from its promise |

The ordering follows what each question demands of a reader. Naming the
subjects in an opening is answerable from one place in the script, and there
Jev tracks the current judge. Counting beats across a whole script, and
measuring the distance between two turns far apart, is where it stops tracking:
on humor it returned 0.85 to 0.98 for every script the judge spread between
0.42 and 0.94, and it gave episode 207 a 0.31 for cliffhangers where the
judge's anchors put it at 1.00.

A single whole-script rubric is worse than any of the three: 12 of 19 scripts
came back at exactly the top level, Spearman +0.45 carried entirely by the four
lowest-scoring episodes. Jev discriminates where a rubric level names something
countable and present in one spot, not where it names a quality of the whole.

This is the same boundary [[jev-decisions-endpoint]] found on articles, where
the endpoint won the closed already-covered question and lost the scoring one.
Nothing measured so far contradicts the rule that Jev is for closed questions
over state the caller has already assembled.

## What this leaves open

An evaluation of Jev against the parts of `EpisodeScoringService` that are
genuinely closed (did this script open with a teaser at all, does it contain a
sponsor read) has not been run. The measurement here is against the current
judge's output and not against a human label, so it measures agreement with
this project's scorer and not correctness, the same limitation
[[jev-decisions-endpoint]] records for its own numbers.
