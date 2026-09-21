# Design

## Where the gate lives

Inside `TopicDedupFilter.filter`, ahead of `buildPrompt`, not as a new
`LlmPipeline` stage.

Two callers reach the dedup stage: `LlmPipeline.dedup` for generation and the
preview path. A new stage would have to be wired into both, and the preview
path does no cost accounting, so the gate's spend would vanish on one of them.
Putting it behind the existing seam means both callers get it, the candidate
list is filtered in the one place that owns the candidate list, and
`DedupFilterResult` stays the single thing the stage returns.

## The request shape

One request per dedup run: one `state` carrying the covered topics and every
candidate, and one `noul` question per candidate keyed `a<articleId>`.

The batching is the design, not a tuning choice. Per-candidate requests re-send
the covered-topics record every time and measured at $0.00605 for 60
candidates, against $0.00074 for the same 60 in one request and $0.00354 for
the clustering call the gate is meant to relieve. Jev's per-call price is low
precisely because the endpoint expects shared context to be shared.

Question keys carry the article id so answers map back without a positional
index. Ids are stable and already the currency of the cluster response.

## Candidate summaries are truncated to 300 characters

Measured over the planted-candidate set, on separation of already-covered from
fresh:

| summary | input tokens | cost | AUC | caught at `>= 0.8` |
|---|---|---|---|---|
| 800 chars | 17,731 | $0.00074 | 0.97 | 20/20 planted, 5/40 fresh |
| 300 chars | 13,036 | $0.00055 | 1.00 | 20/20 planted, 5/40 fresh |
| title only | 9,078 | $0.00038 | 0.95 | 18/20 planted, 3/40 fresh |

300 characters is cheaper and faster than 800 and separates at least as well,
which is unsurprising: the question is whether the candidate is *about* an
already-covered topic, and a topic is legible from the opening of a summary.
Dropping the summary altogether goes too far and loses two genuinely covered
candidates, so titles alone are not enough, unlike in the historical block of
the clustering prompt where titles are all that is needed.

## Chunking is driven by size, not by candidate count

Measured ceiling: 160 candidates at 36,924 input tokens succeeds, 180 at an
estimated 41,000 fails with `{"error_type":"max_tokens_exceeded"}`. With
summaries at 300 characters, 240 candidates fit in 35,648 tokens. The limit is
the request's size, so the candidate count alone cannot predict it: a day of
long summaries hits it sooner than a day of short ones.

The gate therefore splits its candidates into chunks whose serialised request
stays under a configured character budget, defaulting to 150,000 characters
(154,644 succeeded, 172,919 did not). Each chunk repeats the covered topics,
which is the cost of chunking and the reason the budget is set near the
ceiling rather than comfortably below it. Observed candidate counts reach 183
on a busy day, so with 300-character summaries the common case remains a
single request.

A chunk that fails does not fail the run. Its candidates are simply
unanswered, and an unanswered candidate is treated as not covered.

## Threshold

`noul >= 0.8`, configurable, inclusive.

At that point the gate caught all 20 planted already-covered candidates and
flagged 5 of 40 fresh ones, against the clustering call's own 4 of 40. The
distributions are far apart (planted mean 0.95, fresh mean 0.39), so the exact
cut is not delicate; 0.8 sits in the gap rather than on a slope. Erring high is
deliberate: a false exclusion silently removes a story from the episode, where
a false inclusion only leaves the clustering call the work it does today.

## Failure behaviour

`JevClient` never throws. Every failure path returns an empty answer set, and
`TopicDedupFilter` treats an unanswered candidate as not covered, so the
degenerate case is exactly today's behaviour plus the gate's own latency.

This is not defensive habit. During this change's own measurements the endpoint
returned `503 no healthy upstream` to three consecutive requests, and minutes
later served 120 questions in 956 ms. A live verification run then hit
`529 system_overloaded` on both chunks. It is alpha, single-provider, and has
no OpenRouter routing fallback behind it, so an outage is not a matter of if.

That frequency is why a transient status is retried rather than simply giving
up: a retry is the only fallback this endpoint has. The backoff is 300ms
rather than the 1s the other external-API retries use, because the gate must
not hold up the stage it serves. Three attempts then bound the worst case at
about a second of backoff against a gate that answers in 1.6s. A rejected
request is not retried at all, since an oversized batch and a bad credential
fail the same way every time.

## The gate cannot end a run

A gate that excludes every candidate is overruled, and the full list is
clustered anyway.

An empty filter result is not a neutral outcome: `LlmPipeline.dedup` reads it
as "every topic was already covered" and skips the episode without an error,
which on a quiet day is right. That makes a gate malfunction excluding
everything indistinguishable from a quiet day, and it would cost an episode
silently, which is the shape of the episode 204 failure. The clustering call
already has a degeneracy check for exactly this reason. Ending a run stays its
decision, and the gate's job is narrowed to shrinking its input.

## Cost accounting

`DedupFilterResult` gains a nullable gate cost in USD, kept separate from the
clustering call's `TokenUsage`.

Folding Jev's tokens into that `TokenUsage` would be wrong twice over: the
token counts belong to a different model with different rates, and
`CostEstimator.resolveLlmCost` would then estimate from the dedup model's rate
whenever the provider reported nothing. Keeping the gate's reported USD
separate lets `LlmPipeline.dedup` add it to `dedupReportedCostCents` after
`resolveLlmCost` has done its work on the clustering call alone, and lets a
null stay null rather than becoming a zero indistinguishable from a free call.

## Configuration

Under `app.llm.dedup`, so the gate is configured where the stage it serves is:
whether it is enabled, the decisions URL, the model id, the threshold, the
summary truncation and the chunk character budget.

The decisions URL is configuration rather than being derived from the stored
OpenRouter base URL, which points at `/api/v1` for the chat API. Deriving one
from the other by string surgery would couple the gate to the shape of a value
a user can edit in the UI.

## What is deliberately not done

Clustering, per-cluster selection and `previousContext` stay in the generative
call. None is a closed decision: clustering is a partition with no fixed label
set, and the other two depend on it.

`newsType` is not moved to Jev. Measured, 13.7% of classified articles are
EVERGREEN but only 10 of 97 scored above the relevance threshold, so a
newsType gate ahead of scoring would catch 1.4% of articles beyond what a
relevance gate already catches, and `ArticleEligibilityService.dropEvergreen`
already catches them one stage later. Article scoring is not moved either: the
scoring call generates a summary, so a split pays for the article body twice
and measured 20% more expensive. Both are recorded in
`knowledge/references/jev-decisions-endpoint.md`.
