## MODIFIED Requirements

### Requirement: Dedup filter bounds its output with a candidate-scaled token budget
`TopicDedupFilter` SHALL cap the dedup call's output tokens with a budget derived from the number of candidate articles, rather than a fixed value. The budget SHALL be `90 * candidateCount`, clamped to a floor of 8000 and a ceiling of 32000 tokens.

The budget SHALL scale with the candidate count because the prompt requires every candidate article to appear in exactly one cluster, so a legitimate response's length is proportional to its input: observed runs returned 29 clusters for 37 candidates, 39 for 39, 50 for 56 and 44 for 68. At roughly 90 output tokens per cluster (topic label, status, a CONTINUATION's `previousContext` sentence and the selected ids), a 183-candidate day needs some 16,000 tokens. A fixed 8000-token cap truncated episode 191's response mid-array at cluster 234 and cost the episode.

The ceiling SHALL be retained so that a degenerating response (for example a repetition loop emitting near-duplicate clusters indefinitely) is still cut off in seconds rather than streaming for minutes. The ceiling SHALL stay well inside the dedup model's context window.

#### Scenario: Budget scales with a large candidate set
- **WHEN** dedup runs with 183 candidate articles
- **THEN** the request's output-token cap is 16470, not 8000

#### Scenario: Small candidate set gets the floor
- **WHEN** dedup runs with 12 candidate articles
- **THEN** the request's output-token cap is the 8000-token floor, not 1080

#### Scenario: Very large candidate set is bounded by the ceiling
- **WHEN** dedup runs with 900 candidate articles
- **THEN** the request's output-token cap is the 32000-token ceiling, not 81000

### Requirement: Dedup filter salvages a truncated response, and otherwise fails the episode
The dedup stage SHALL use a non-reasoning model (default `deepseek/deepseek-v4-flash`). Reasoning models are unsuitable because their reasoning tokens are charged against the output-token cap and can return empty content, which previously caused dedup to be silently skipped.

`TopicDedupFilter` SHALL parse the response strictly first. When the strict parse fails because the response is truncated, the filter SHALL recover the complete cluster objects from the incomplete `clusters` array instead of discarding the response. A truncated dedup response is safe to act on because an article that no surviving cluster mentions is simply not selected for composition, which is the conservative outcome.

A salvaged response SHALL be accepted only when it still selects at least `app.compose.max-articles` articles. At or above that count the truncated tail provably could not have changed what gets composed, because the compose cap would have discarded the surplus anyway. A salvage SHALL be logged at WARN with the recovered cluster count and the selected article count.

`TopicDedupFilter` SHALL retry the dedup LLM call (at least once) when neither a strict parse nor an acceptable salvage is available. If no attempt yields either, the error SHALL propagate: the system SHALL NOT silently fall back to composing un-deduped articles. During episode generation a propagated dedup failure SHALL fail the episode (status `FAILED`, retryable). During preview a propagated dedup failure SHALL be surfaced as an error to the caller.

#### Scenario: Truncated response with enough clusters is salvaged
- **WHEN** the dedup response is cut off mid-array after 234 complete clusters that together select at least `app.compose.max-articles` articles
- **THEN** the 234 clusters are used, a WARN records the salvage, and the episode is composed from them

#### Scenario: Truncated response with too few clusters is retried
- **WHEN** the dedup response is cut off after 3 complete clusters selecting 5 articles and `app.compose.max-articles` is 40
- **THEN** the attempt fails and the retry applies rather than composing from 5 articles

#### Scenario: Articles beyond the truncation point are not composed
- **WHEN** a salvaged response's surviving clusters mention only some of the candidate articles
- **THEN** the unmentioned candidates are not selected for composition

#### Scenario: Dedup failure fails the episode
- **WHEN** the dedup LLM call yields neither a strict parse nor an acceptable salvage on every retry during episode generation
- **THEN** the episode is marked FAILED and is NOT composed with un-deduped articles

#### Scenario: Dedup failure surfaces during preview
- **WHEN** the dedup LLM call fails on every retry during a preview
- **THEN** the preview reports an error rather than returning an un-deduped script

#### Scenario: Empty filtered result still skips the episode (not an error)
- **WHEN** dedup succeeds but every cluster selects no articles (all continuations with no new developments)
- **THEN** the pipeline skips briefing generation for that run without raising an error
