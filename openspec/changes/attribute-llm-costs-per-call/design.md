## Context

See proposal.md - Why for the motivation. The state that shapes the approach:

- `llm_calls` already holds one row per request, written outside the pipeline's transaction, with
  `episode_id` (since V72), `article_id` (since V74), tokens, and a nullable `reported_cost_usd`.
  The two calls of episode 226's failed run survive there.
- The cost *resolution* (reported value, else configured rates, else nothing, plus the
  `LlmCostSource` that says which) runs in `LlmPipeline` at the end of a stage and lands only in the
  episode columns, inside the transaction that a failed run rolls back.
- Scoring and recap both run on the podcast's filter model and both record themselves under the
  `filter` stage, while the breakdown bills them separately. They are told apart by what the request
  was issued for: a scoring request names its article, a recap request names its episode.
- Stages differ in how they can be attributed. Dedup, dedup-gate, compose and recap run inside a
  generating episode and carry `episode_id` directly. Scoring runs when an article is polled, long
  before any episode exists, and reaches an episode only through `episode_candidate_articles`.
- Episode 226 measured: dedup and dedup-gate are fully attributed (2 calls each, both runs), while
  111 of 178 scoring calls can be attributed.

## Goals / Non-Goals

**Goals:**

- One record of what a request cost, written when the request completes.
- An episode's cost derived from those records wherever they are complete.
- A stage's request count identical in the latency view and the cost breakdown.

**Non-Goals:**

- Changing the shape of the episode cost API. Readers see the same fields.
- Reconstructing costs for episodes whose requests were never attributed. Below the boundary the
  stored columns stay the answer.
- Reworking TTS cost tracking, which does not go through `llm_calls`.
- Changing what the pipeline spends or which models it uses.

## Decisions

### Cost is resolved where the call is recorded

The resolution moves from the stage end into the telemetry recording path, so the cost is written in
the same insert as the request it belongs to. That single move is what makes a failed run's spend
survive; everything else here is a consequence of it.

*Alternative considered*: keep resolving at stage end and additionally write the cost immediately.
Rejected because it creates a third writer of the same number, which is the defect being fixed.

### `llm_calls` carries the resolved cost in USD, beside the reported one

The table already holds `reported_cost_usd` in USD, matching what the provider reports. The resolved
cost joins it in the same unit, with its source, and the conversion to fractional cents happens once
on the read path where the breakdown is served.

*Alternative considered*: store fractional cents to match the episode columns. Rejected because the
row would then mix units, and the reported and resolved values of one call could not be compared
without converting one of them.

### The fallback gate is per stage, not per episode

A stage reports from the requests when its attribution is complete, and from its persisted column
when it is not. Completeness is established differently per stage, because the two kinds of stage
fail differently:

- Episode-tagged stages (dedup, dedup-gate, compose, recap): complete when the episode was generated
  after requests began naming an episode, which `earliestAttributedStart` already derives.
- Scoring: complete when every one of the episode's recorded candidates has a scoring request
  attributed to it. This is a count against `episode_candidate_articles`, the table the candidate fix
  just made functional.

This is what makes episode 226 correct immediately: its dedup and gate stages project from the
requests and pick up the failed run, while its score stage, attributed 111 of 178, keeps its column.
A per-episode gate would instead force all of 226 back to the columns and leave the original defect
in place.

*Alternative considered*: a single per-episode gate. Rejected as above. *Alternative considered*: no
gate, project everything. Rejected because it halves the score stage, the most expensive one.

### Historical `article_id` is backfilled only where the match is unambiguous

An article carries the exact figures its scoring call returned (`llm_input_tokens`,
`llm_output_tokens`, `llm_reported_cost_usd`), so a request is matched to an article on what the
provider actually stated rather than on a timestamp. The link is made only when the match is unique
in both directions and the article is not already claimed. Where several match, none do, or the
request failed and so reported no tokens, it stays unattributed and the stage falls back to its
column.

*Alternative considered*: a nearest-timestamp heuristic. Rejected because a wrong link moves real
money onto the wrong episode, which is worse than the gap it closes, and because the gate makes the
gap harmless.

### The episode columns are kept, not dropped

They remain both the answer below the boundary and the fallback for an incompletely attributed
stage. The pipeline keeps writing them, so a stage that later loses attributability still has a
value, and the change is reversible by reading them again.

*Alternative considered*: drop the columns once the projection works. Rejected: it would make every
pre-boundary episode report nothing, and there is no way back.

## Risks / Trade-offs

- **Two sources still exist, so they can still disagree** → They are no longer both authoritative:
  the projection wins wherever it applies, and the fallback is explicit per stage. A test asserts the
  two agree for a fully attributed episode.
- **Projecting on every read is slower than reading a column** → The scope is one episode's requests,
  already indexed by `(episode_id, stage)`. Measure it against the episode detail read before
  accepting; if it bites, cache per episode rather than reintroducing a written total.
- **The backfill's uniqueness rule may match very little** → Acceptable by design: unmatched requests
  leave the score stage on its column, which is what happens today. Report the coverage it achieved
  rather than assuming it worked.
- **A cost resolved per call rounds differently than one resolved per stage** → Keep the per-call
  value at full precision in USD and round once when serving cents, so a stage of many small calls
  does not round each of them to zero. This is the same trap the score stage already documents.
- **Recording now does slightly more work inside the call path** → The existing requirement that
  recording never affects the call it measures still holds and is already covered by a scenario;
  resolution is arithmetic over values already in hand, with no I/O.

## Migration Plan

1. V76 adds the resolved-cost and source columns to `llm_calls`, nullable.
2. V77 backfills `article_id` under the uniqueness rule. It links 1086 of 1643 eligible requests;
   episode 226 reaches 151 of its 178 candidates, so its score stage stays on its column.
3. V78 copies `reported_cost_usd` into the resolved cost of every row that has one, marking a cache
   hit as a replay. This is a copy rather than a reconstruction: the provider stated that charge, and
   without it every historical request counts as unresolved and holds its stage on the columns,
   including the six requests of episode 226 that this change exists to recover. A row that reported
   nothing stays null, because what the rate table said at the time is gone.
4. The per-call resolution and the gated projection ship together, with the pipeline still writing
   the episode columns.
5. Verify episode 226 reports its first run's dedup and gate calls, and that its score stage is
   unchanged.

Rollback is step 4 in reverse: the episode columns are still being written, so reading them again
restores today's behavior without data loss.
