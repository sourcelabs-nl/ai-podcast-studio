## ADDED Requirements

### Requirement: Dedup logs an abnormal finish reason
`TopicDedupFilter` SHALL log the dedup response's finish reason at WARN whenever it is anything other than a normal stop, together with the output-token budget the request carried. When the finish reason is a normal stop, or when the provider reports no finish reason at all, no such log SHALL be emitted.

Budget exhaustion and a transport fault were previously indistinguishable in the logs. A response the model cut off at the cap raises `OpenAIInvalidDataException("Error reading response")` from the client, the same message a read timeout produces, so episode 200's five failed attempts gave no indication that the budget was the cause. The finish reason is the field that separates them: the first run after this was added reported `LENGTH against an output budget of 23940 tokens`, which identified the cause immediately.

#### Scenario: A truncated response names the budget
- **WHEN** the dedup response's finish reason is `LENGTH` for a request whose output-token budget was 23940
- **THEN** a WARN records the finish reason and that budget

#### Scenario: A normal completion logs no warning
- **WHEN** the dedup response's finish reason is a normal stop
- **THEN** no finish-reason warning is logged

#### Scenario: A missing finish reason logs no warning
- **WHEN** the provider reports no finish reason
- **THEN** no finish-reason warning is logged, because a provider that reports nothing says nothing about the budget

## MODIFIED Requirements

### Requirement: Dedup filter salvages a truncated response, and otherwise fails the episode
The dedup stage SHALL state an explicit reasoning effort of `none` rather than omitting the reasoning block, and SHALL NOT rely on the model it runs on to be non-reasoning.

The dedup model reasons by default: OpenRouter reports `~deepseek/deepseek-v4-flash-latest` as `default_enabled: true` at `default_effort: "high"`, and an absent reasoning parameter is inferred from that default. Because reasoning tokens are charged against the same output-token cap as the JSON, omitting the block consumed the whole budget and returned empty content, failing episode 200 on five successive attempts. Measured on the live API for that model, an omitted block cost 47 reasoning tokens on a one-line task where an explicit `effort: "none"` cost none; a bounded `effort: "low"` cost 22 on the same task but still consumed a full 23,940-token dedup budget on a real prompt, so bounding the effort is not sufficient and only `none` is.

`TopicDedupFilter` SHALL parse the response strictly first. When the strict parse fails because the response is truncated, the filter SHALL recover the complete cluster objects from the incomplete `clusters` array instead of discarding the response. A truncated dedup response is safe to act on because an article that no surviving cluster mentions is simply not selected for composition, which is the conservative outcome.

A salvaged response SHALL be accepted only when it still selects at least `app.compose.max-articles` articles. At or above that count the truncated tail provably could not have changed what gets composed, because the compose cap would have discarded the surplus anyway. A salvage SHALL be logged at WARN with the recovered cluster count and the selected article count.

`TopicDedupFilter` SHALL retry the dedup LLM call (at least once) when neither a strict parse nor an acceptable salvage is available. If no attempt yields either, the error SHALL propagate: the system SHALL NOT silently fall back to composing un-deduped articles. During episode generation a propagated dedup failure SHALL fail the episode (status `FAILED`, retryable). During preview a propagated dedup failure SHALL be surfaced as an error to the caller.

#### Scenario: Dedup requests no reasoning
- **WHEN** the dedup request is built for an `openrouter` model
- **THEN** the extra body's `reasoning` object carries `effort` `none` and `exclude`, rather than the block being omitted

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
