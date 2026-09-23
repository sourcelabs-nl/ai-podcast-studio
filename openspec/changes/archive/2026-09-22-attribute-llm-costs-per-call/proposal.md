## Why

An episode's LLM cost is written at the end of each pipeline stage, inside the same transaction as
the stage's results. A run that pays for its calls and then fails before that write leaves no cost
behind: episode 226 issued a dedup and a dedup-gate call on its first run, failed on the save, and
after a retry reports only the retry's calls. $0.0129 of real spend is attributed to nothing.

The same episode shows the second half of the problem. Its cost columns and the request log disagree
in both directions: the log holds two dedup calls where the cost breakdown holds one, and the cost
breakdown covers 178 scored articles where the log can attribute only 111. Two independently
maintained answers to the same question have drifted, and neither is complete on its own.

## What Changes

- Each `llm_calls` row carries the resolved cost of that call in cents and the source it came from
  (provider-reported or estimated), written with the row itself rather than derived later at stage
  end. The cost resolution that today runs in the pipeline moves to where the call is recorded.
- An episode's per-stage cost becomes a projection over its recorded calls instead of a separately
  maintained set of columns. A call that was paid for is counted because it was made, and no
  transaction rollback can remove it.
- A failed run's calls stay attributed to the episode that issued them. A `retry` keeps the episode,
  so both attempts land on it and its cost is what it actually cost. A `rerun` creates a new episode,
  and the failed one keeps its own calls rather than handing them over.
- Score calls become attributable for the episodes the pipeline can still resolve, by backfilling
  `llm_calls.article_id` where the article is identifiable. **This is a precondition, not a
  side-effect**: a projection over a log that can attribute only 111 of 178 score calls would halve
  the most expensive stage of the pipeline.
- Below the attribution boundary the stored columns remain the answer. Episodes generated before
  requests carried an episode cannot be projected, and `earliestAttributedStart` already marks where
  that changes.
- Candidate articles are recorded row by row rather than through `saveAll`, which batched the inserts
  and then failed on generated keys the SQLite driver returns none of from a batch. This is a
  precondition too: the score stage's completeness check counts against those rows, and until this
  fixed itself the table was empty for every episode since it shipped.
- Episode 226 is corrected to include its first run's calls.

## Capabilities

### New Capabilities

None. Both affected capabilities exist.

### Modified Capabilities

- `cost-tracking`: an episode's per-stage cost is projected from its recorded calls rather than
  persisted per stage; a run that failed after paying is included; the stored columns become the
  answer only below the attribution boundary.
- `llm-call-telemetry`: each record carries the resolved cost and its source, and a scoring request
  names the article it scored so it can be attributed to the episode that paid for it.

## Impact

- `store/LlmCallRepositoryCustom.kt`: per-stage cost projection alongside the existing latency and
  request queries, over `BELONGS_TO_EPISODE`.
- `llm/` recording path: resolves and writes the cost with the call; `CostEstimator` and
  `LlmCostSource` are applied per call rather than per stage.
- `llm/LlmPipeline.kt`, `podcast/EpisodeService.kt`: the stage saves no longer own the cost figures.
- A migration adds the cost columns to `llm_calls` and backfills `article_id` where resolvable.
- The episode cost columns are retained for episodes below the attribution boundary, so no episode
  loses the cost it already reports. **No BREAKING change to the episode API shape.**
- `frontend-llm-latency` and the episode cost breakdown read the same source afterwards and can no
  longer disagree on how many calls a stage made.
