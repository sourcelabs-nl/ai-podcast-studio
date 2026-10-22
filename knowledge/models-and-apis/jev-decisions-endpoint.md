---
okf_version: "0.2"
type: finding
title: What Jev can decide for this pipeline, and what it cannot
answers: deciding where Jev can carry a call in this pipeline and where it cannot, including whether to gate the scoring stage with it
status: stable
generated:
  by: claude-sonnet-5-5
  at: 2026-09-30T00:00:00Z
method: >
  Three runs against the live APIs from this project's OPENROUTER_API_KEY.
  (3) 300 random scored articles from September 2026, each put through Jev as
  a relevance gate reading the title plus the first 0, 300, 800, 2,000 or 6,000
  characters of the body, or the whole body, against the stored score as the
  reference. The first two runs:
  (1) 40 articles drawn from the last 8 episodes via the REST API, each put
  through the current combined scoring prompt and through the split
  (Jev decisions + summary-only chat call), with the arm order alternated per
  article and both chat arms sending the real OpenRouterRouting extra body
  (fp8 floor, require_parameters, reasoning effort "none", exclude). (2) 80
  articles, 40 above and 40 below the relevance threshold, scored by Jev alone
  to test it as a pre-filter. Cost read from each response's own usage.cost,
  latency measured around the HTTP call.
model_version: typesafe/jev-1.13-20260917
stale_after: 2026-12-21T00:00:00Z
---

# What Jev can decide for this pipeline, and what it cannot

Jev is TypeSafe's decision model: it takes a `state` object and a record of
typed `questions` and returns constrained answers, never free text. It is not
reachable through `/api/v1/chat/completions` and does not appear in OpenRouter's
`/api/v1/models` listing, so nothing built on Spring AI's `ChatModel` can call
it. It lives on `POST /api/alpha/decisions`, alpha, and is called with a plain
HTTP client.

## The answer shapes

- `noul`: a probability in `[0,1]` for a stated proposition. Takes
  `instructions` only.
- `score`: a position along an ordered rubric, plus the per-level `legend`,
  `probabilities` and a `confidence`. Requires `criteria` as an **array** of
  level descriptions, lowest first.
- `choice`: one label from a fixed set, plus per-label probabilities and a
  `confidence`. Requires `criteria` as a **record** of label to description.

Several questions run in one call against one shared `state`. Omitting
`criteria` on a `score` or `choice` is a 400, not a default.

## The request has a size ceiling, and it is not a question count

An oversized request is rejected with
`400 {"error_type":"max_tokens_exceeded"}`. The ceiling is the request's
size, so no question count predicts it. Measured with the same question text
throughout: 160 candidates at 36,924 input tokens succeeded and 180 did not,
while cutting each candidate's summary from 800 characters to 300 fitted 240
candidates into 35,648 tokens. By serialised request body, 154,644 characters
succeeded and 172,919 did not.

Availability is what the alpha label means. Every failure observed so far falls
inside one OpenRouter incident on the morning of 2026-09-21, between roughly
08:20 and 09:30 CEST, during which OpenRouter's own availability graph for the
model dips to about 75%: three consecutive `503 no healthy upstream`, then the
same request served in 956 ms during a recovery spike, then both chunks of a
live pipeline run rejected with `529 system_overloaded` ("We are currently
experiencing high traffic"). Outside that window the endpoint has answered every
request, in 686 ms at 12:30 the same day. The steady-state failure rate is
therefore unmeasured, and the two-of-three failure rate seen that morning
describes the incident rather than the endpoint.

What the incident does establish is the shape of a failure rather than its
frequency. Neither status is retried by anything in front of the caller, because
a single-provider model has no OpenRouter routing fallback: when that provider
degrades, there is nothing to route to, whatever the platform's general
load-balancing note says. Anything calling this endpoint needs a defined
behaviour for having no answer, and will exercise it.

## `score` takes an ordered rubric, not a checklist

The `criteria` array is a set of ordered levels, and the answer is the expected
position along them, returned with the `legend` that names each level. Passing
independent conditions instead ("is on topic", "is substantive", "a listener
would want it") inverts the result: measured against the current scorer over
the same 40 articles, a checklist gave a Spearman rank correlation of **-0.61**
and a four-level rubric of **+0.71**. Divide the returned score by
`len(criteria) - 1` to get a `[0,1]` value.

## Jev is fast and cheap; the article read is what costs

450-500 ms and $0.00004 per call, stable across 120 calls, output tokens
billed at zero. That is about a tenth of what the scoring model costs on the
same article, and the scoring model's cost is almost entirely its input: the
article body.

## Splitting the scoring stage loses

Over 40 articles (median 668 words), the current combined call against the
split:

| | combined | Jev + summary-only |
|---|---|---|
| cost per article (median) | $0.000302 | $0.000361 (**+20%**) |
| input tokens | 1,676 | 1,904 (Jev) + 1,403 (summary) |
| latency (median) | 3,506 ms | 3,994 ms (460 + 3,499) |

The split loses because both calls read the whole article, so the body is paid
for twice. Jev is only 16% of the split's cost; the duplicated read is the
rest. `ArticleScoreSummarizer` returns a summary in the same call as the score
and Jev cannot generate text, so there is no arrangement in which Jev replaces
that call rather than adding to it.

## As a pre-filter it pays only when it reads an excerpt, not the body

43% of all scored articles (5,011 of 11,650) fall below the relevance
threshold after a summary has already been generated for them. A gate saves
what scoring would have spent on those, minus what deciding costs, and what
deciding costs depends on how much of each article Jev reads.

**Reading the whole body**, over 80 articles split evenly above and below the
threshold: **AUC 0.90**, Spearman **+0.79** against the stored scores. Gating
on the normalised Jev score, against the scoring model's own verdict as the
reference:

| cut | dropped | good articles lost | cost per article |
|---|---|---|---|
| 0.30 | 15% | 0 | -5% |
| 0.40 | 30% | 3 (92% recall) | -20% |
| 0.50 | 55% | 10 (75% recall) | -45% |

Zero-loss operation saves about 5%. Everything past that trades recall for
money. A chat-model gate would not do better: a score-only chat call still
reads the same body, so it costs nearly what the combined call costs, which is
why Jev is the right mechanism for a gate.

**Reading title plus an excerpt**, over 300 random articles (120 at or above
the podcast's then threshold of 5), scored as a `score` question against the same
four-level rubric, batched so a request stays under the size ceiling. Savings
are the recorded scoring cost of the dropped articles minus the gate's own
cost:

| text sent | AUC | gate cost as share of scoring | cut 0.20: dropped, relevant lost, net saving | cut 0.30 | cut 0.40 |
|---|---|---|---|---|---|
| title only | 0.85 | 2.6% | 56%, 29 of 120, 40% | 61%, 35, 45% | 70%, 44, 56% |
| 300 chars | 0.89 | 3.5% | 29%, 1 of 120, **20%** | 35%, 5, 25% | 46%, 10, 39% |
| 800 chars | 0.92 | 4.3% | 30%, 0 of 120, 20% | 33%, 1, 22% | 45%, 7, 37% |
| 2,000 chars | 0.93 | 5.8% | 28%, 0 of 120, 17% | 34%, 0, 20% | 43%, 4, 30% |
| 6,000 chars | 0.93 | 9.4% | 30%, 0 of 120, 15% | 35%, 2, 17% | 46%, 7, 28% |
| whole body | 0.94 | 15.3% | 28%, 0 of 120, 6% | 33%, 1, 10% | 43%, 4, 21% |

- Title alone is too coarse: even the lowest cut loses about a sixth of the
  relevant articles.
- 300 to 800 characters is the useful range. Beyond 2,000 the gate's own
  input eats the saving, and the whole-body row reproduces the 5% above.
- Dropped share barely moves with the text sent (28 to 30% at cut 0.20); more
  text loses fewer relevant articles rather than dropping more.
- Among the articles that would reach an episode (stored score 8 or more, 47 of
  them), 800 characters at cut 0.30 lost none and 300 characters lost 2.
- Latency: 300 articles took 3.2 s end to end in six batches of 50 at 800
  characters, against about 140 s to score them (4.6 s mean per call over 4,118
  calls in 14 days, 10 concurrent). Dropping a third of the candidates shortens
  scoring by about 45 s per 300, and scoring runs in the background after a poll.

The 5% figure therefore describes a gate that reads the whole article, not a
gate in general.

## Where it does fit

Closed-set classification, where there is no text to generate and so nothing
is read twice. On `newsType` it agrees with the scoring model on **34 of 40**
articles; the six disagreements are almost all Jev calling DEVELOPMENT where
the scorer said RETROSPECTIVE, so it is the more permissive of the two on that
boundary. On the openspec.dev landing page that leaked into episode 222 (see
`ArticleEligibilityService.dropEvergreen`) it returned EVERGREEN at confidence
0.99 from the pipeline's own three descriptions pasted in as `criteria`,
with no other prompt engineering.

The eligibility stage also drops stale developments by the scoring model's stated
development date: see [[development-date-extraction-for-stale-news]].

## Dedup: batch the questions against one state

The already-covered question is a `noul`, and it is the one place measured so
far where Jev clearly beats what the pipeline does. Over 60 candidates (the 40
articles of one episode plus 20 drawn from the previous episode, whose topics
are provably in the covered list) against 148 covered topics and 200
historical titles:

| | current dedup call | Jev per candidate | Jev batched |
|---|---|---|---|
| calls | 1 | 60 | 1 |
| latency | 22,879 ms | 60 x 538 ms | **857 ms** |
| cost | $0.00354 | $0.00605 | **$0.00074** |
| planted caught | 20/20 | 20/20 | 20/20 |
| AUC planted vs fresh | | 1.00 | 0.99 |

One call carrying one `state` and 60 `noul` questions costs a fifth of the
current dedup call and returns in a twenty-seventh of the time. Asking the
same question in 60 separate calls costs nearly **twice** the current call,
because each one re-sends the 148 covered topics. This is the general rule for
this endpoint: its per-call price is low, but shared context sent per item
destroys that, and the questions record is what amortises it.

At `noul >= 0.8` Jev flags the same 4 of 40 fresh candidates as already
covered that the current dedup call does, so the two agree on the boundary as
well as on the planted cases.

How much of each candidate to send is a real choice, and more is not better:

| summary sent | input tokens | cost | AUC | caught at `>= 0.8` |
|---|---|---|---|---|
| 800 chars | 17,731 | $0.00074 | 0.97 | 20/20 planted, 5/40 fresh |
| 300 chars | 13,036 | $0.00055 | 1.00 | 20/20 planted, 5/40 fresh |
| title only | 9,078 | $0.00038 | 0.95 | 18/20 planted, 3/40 fresh |

300 characters separates at least as well as 800 for two thirds of the price,
because the question is whether a candidate is *about* an already-covered
topic and a topic is legible from the opening of a summary. Titles alone go
too far and miss two genuinely covered candidates, unlike in the clustering
prompt's historical block where titles are enough.

## What the gate does on real traffic

Running in the pipeline against a live candidate set of 186 articles and 190
covered topics, the gate excluded 45 of them (24%) in 1.6 seconds, and the
same run repeated gave the same 45. The whole dedup stage went from 20.9
seconds gated to 11.8 on the second run and 36.1 with the gate disabled: the
clustering call gets faster too, because the prompt it receives is a quarter
smaller.

Do not let the gate end a run. An empty dedup result makes the pipeline skip
the episode without an error, so a gate excluding every candidate is
indistinguishable from a genuinely quiet day and would cost an episode
silently. The gate's exclusions are overruled when they would empty the list,
and deciding that nothing is left to say stays with the clustering call, whose
response is already checked for degeneracy.

Chunk evenly rather than greedily. A greedy fill split those 186 candidates
184 and 2, and because every chunk repeats the covered topics the second
request carried the whole 190-topic list to ask about two articles.

## What Jev cannot take over in dedup

`TopicDedupFilter` does three things in one call, and only one is a closed
decision. Clustering candidates by topic is a partition with no fixed label
set; `previousContext` is a generated sentence; selecting up to three articles
per cluster depends on both. Jev can answer NEW versus CONTINUATION and
nothing else, so it shrinks that call's input rather than replacing it.

## The money is not the reason

Summing every measured saving at zero recall loss: about 20% of the filter
stage with an excerpt gate (5% with a whole-body gate) and roughly a fifth of
the dedup stage, against $1.46 and $0.25 of recorded spend over the 30 days to
2026-09-30. At about 5,400 scored articles a month and $0.00032 per article,
the excerpt gate is worth about $0.35 a month. That is cents per month, so it
is not built; it is worth revisiting if article volume or the scoring model's
price grows about tenfold. Per-source yield is in [[source-yield-2026-09]].

The case for Jev here is that the dedup call's documented failures (episode 191 truncated, 200 returned empty,
202 unparseable, 204 composed from a single article) are all failures of one
large generative call doing too much, and a constrained-output call cannot
fail in any of those ways. Latency is the other real gain: 857 ms against 23
seconds for the decision. The filter stage's p50 is 2.2 s per article (4.6 s
mean, 4,196 calls in the same 30 days), which an excerpt gate shortens only by
dropping a third of the articles it would score.

## Calling it

Not through Spring AI. `RestClient`, as [[server-side-web-search-on-the-jvm]]
describes for Tavily and as `TavilyClient` implements, with its own request and
response types. Nothing it does passes through `ChatClientFactory`,
`CachingChatModel` or `CostEstimator`, so cost logging would have to be called
explicitly from the client.

Alpha endpoint, one provider, no fallback: a Jev outage has no OpenRouter
routing behind it. A model that takes the same request on the same endpoint,
with more than one provider, is described in
[[decision-model-and-eu-hosting-alternatives]].

## The gate's calls are visible as ordinary LLM requests

A Jev call is recorded in `llm_calls` like any other request, under its own
stage `dedup-gate` rather than under `dedup`: the clustering call runs about 23s
and the gate about 0.6s, so one set of percentiles over both describes neither.
Measured on the first live run with the gate enabled, over 188 candidates split into
two chunks: two requests, p50 512ms and p95 895ms, against the dedup stage's p50
of 20.9s.

One row is written per HTTP attempt rather than per `ask`, so a retried 529 is
visible as a failure instead of being absorbed into the attempt that succeeded,
and the retry's wait falls outside every recorded duration.

Latency percentiles count a request that succeeded or ran out of time, and skip
cache hits and every other failure. A fast failure reports how quickly the
provider refused rather than how long it takes to answer, and this endpoint's
529 arrives in milliseconds: counting it would make an outage read as a latency
improvement. A timeout is the opposite case and is counted, which means a
saturated stage reads as a p99 at its configured ceiling.

## Judging scripts is a separate question

Whether Jev can replace this project's own script judge is measured in
[[jev-as-a-judge]], against the same endpoint and the same boundary: it tracks
the current judge on what is named in one place and stops tracking on what has
to be counted across a whole script.

## Caveats on the numbers

Every sample comes from one podcast and one scoring model
(`deepseek/deepseek-v4.1-flash`). The reference in every comparison is that
model's own output, not a human judgement, so "agreement" measures
reproducibility of the current gate and not correctness. The excerpt table is a
single random draw of 300 with one hand-written rubric that was not tuned, and
its excerpt is the start of the body, whose usefulness varies by source. The first cost and
latency run was discarded: it omitted the `OpenRouterRouting` extra body, which
routed to a different endpoint mix and made the combined call look ten times
slower than it is. The routing body itself is described in [[openrouter-routing]].
