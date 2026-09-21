# Gate the dedup call with a Jev already-covered decision

## Why

The dedup stage is one generative call doing three jobs at once: clustering
today's candidates by topic, deciding NEW versus CONTINUATION against the
covered-topics record, and writing the `previousContext` sentence plus the
per-cluster selection. Its failures are all failures of that size. Episode 191
was truncated mid-array, episode 200 returned empty content five times,
episode 202 came back wrapped in prose, episode 204 composed from a single
article because 33 of 34 NEW clusters selected nothing. Each cost an episode,
and each is answered in `article-dedup-filter` by another layer of salvage,
validation and budget arithmetic around the same oversized call.

Of the three jobs, exactly one is a closed decision: is this candidate about a
topic we already covered. TypeSafe's Jev model answers that kind of question
with constrained output, so it cannot return prose, an unparseable payload or a
truncated array at all.

Measured on 60 candidates (one episode's 40 articles plus 20 drawn from the
previous episode, whose topics are provably in the covered list) against 148
covered topics and 200 historical titles:

| | current dedup call | Jev, batched |
|---|---|---|
| latency | 22,879 ms | 857 ms |
| cost | $0.00354 | $0.00074 |
| already-covered articles caught | 20/20 | 20/20 |
| fresh articles flagged | 4/40 | 4/40 (`noul >= 0.8`) |

AUC separating the planted from the fresh candidates is 0.99, and at the
operating threshold the two agree on the boundary cases as well as on the
planted ones.

The money is not the point and this proposal does not claim it: across the
whole pipeline the saving is cents per month. The point is that the most
safety-critical decision in the stage moves into a call that cannot fail the
way the dedup call has repeatedly failed, and that the generative call it
still feeds gets a materially smaller candidate list to cluster.

## What Changes

- A `JevClient` calling OpenRouter's decisions endpoint over `RestClient`,
  following `TavilyClient`: a failure degrades to a logged warning and an
  empty answer, never an exception that reaches the pipeline.
- `TopicDedupFilter` asks the gate, before it builds its prompt, which
  candidates are already covered, and drops those from the candidate list it
  clusters. The gate runs as a single request carrying one shared state and
  one `noul` question per candidate.
- A gate that answers nothing, for any reason, leaves the candidate list
  untouched and the stage behaves exactly as it does today.
- The gate's reported cost and its dropped-candidate count are recorded on the
  dedup stage rather than disappearing, since the call does not pass through
  `CachingChatModel` or `CostEstimator`.

Batching is not an optimisation detail but the thing that makes this work at
all. Asking the same question one call per candidate re-sends the covered
topics with every one and costs $0.00605, nearly twice the call it was meant
to relieve. The shared state is what amortises the history.

Out of scope: clustering, selection and `previousContext` stay in the
generative call, because none of them is a closed decision. Jev is not used
for article scoring or for `newsType`, both measured and both rejected, see
`knowledge/references/jev-decisions-endpoint.md`.

## Capabilities

### New Capabilities

- `jev-decisions`: a client for TypeSafe's Jev model on OpenRouter's decisions
  endpoint, covering the request and answer shapes, credential resolution, and
  the rule that a failure never propagates to the caller.

### Modified Capabilities

- `article-dedup-filter`: the filter gains an already-covered gate ahead of its
  clustering call, with the drop rule, the threshold, the degradation
  behaviour when the gate is unavailable, and how the gate's cost is accounted.

## Impact

- New: `JevClient` and its request/response types, plus configuration for the
  endpoint, model id and threshold under `app`.
- Changed: `TopicDedupFilter.filter` and the cost accounting on
  `DedupFilterResult`, which both `LlmPipeline.dedup` and the preview path
  read.
- Credentials: reuses the existing OpenRouter key from
  `UserProviderConfigService`; no new provider to configure.
- Risk: the decisions endpoint is alpha, served by one provider with no
  OpenRouter fallback behind it. The degradation path is what makes that
  acceptable, and it is a requirement rather than a nicety.
