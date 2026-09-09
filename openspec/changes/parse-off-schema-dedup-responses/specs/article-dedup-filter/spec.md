## MODIFIED Requirements

### Requirement: Dedup filter salvages a truncated response, and otherwise fails the episode
The dedup stage SHALL state an explicit reasoning effort of `none` rather than omitting the reasoning block, and SHALL NOT rely on the model it runs on to be non-reasoning.

The dedup model reasons by default: OpenRouter reports `~deepseek/deepseek-v4-flash-latest` as `default_enabled: true` at `default_effort: "high"`, and an absent reasoning parameter is inferred from that default. Because reasoning tokens are charged against the same output-token cap as the JSON, omitting the block consumed the whole budget and returned empty content, failing episode 200 on five successive attempts. Measured on the live API for that model, an omitted block cost 47 reasoning tokens on a one-line task where an explicit `effort: "none"` cost none; a bounded `effort: "low"` cost 22 on the same task but still consumed a full 23,940-token dedup budget on a real prompt, so bounding the effort is not sufficient and only `none` is.

`TopicDedupFilter` SHALL parse from the first brace or bracket in the response rather than from the start of the text, so that a prose lead-in or a markdown code fence is ignored. Episode 202's response was complete, valid JSON and still cost the episode, because it arrived as `**Output:**` followed by a ```json fence. The strict parse SHALL read a single JSON value and stop, so a closing fence or any trailing chatter is ignored without the end of the payload having to be located: a stray bracket in a sign-off must not be mistaken for it. The salvage SHALL read to the end of the response, because a truncated response's last closer sits inside the element it was cut off in and stopping there would discard the complete elements before it.

`TopicDedupFilter` SHALL accept either shape the model answers with: the `{ "clusters": [...] }` object the prompt asks for, or a bare cluster array. Both the strict parse and the salvage SHALL handle both shapes.

`TopicDedupFilter` SHALL parse the response strictly first. When the strict parse fails because the response is truncated, the filter SHALL recover the complete cluster objects from the incomplete cluster array instead of discarding the response. A truncated dedup response is safe to act on because an article that no surviving cluster mentions is simply not selected for composition, which is the conservative outcome.

A salvaged response SHALL be accepted only when it still selects at least `app.compose.max-articles` articles. At or above that count the truncated tail provably could not have changed what gets composed, because the compose cap would have discarded the surplus anyway. A salvage SHALL be logged at WARN with the recovered cluster count and the selected article count.

`TopicDedupFilter` SHALL retry the dedup LLM call (at least once) when neither a strict parse nor an acceptable salvage is available. Every attempt SHALL send a prompt distinct from every other attempt of the same call: the first attempt SHALL send the prompt unchanged, and each subsequent attempt SHALL append a correction that names the attempt number, states that the previous response could not be parsed, and asks for the raw `{ "clusters": [ ... ] }` object with no reasoning, commentary, or markdown code fences.

Sending the byte-identical prompt on a retry cannot succeed. The `llm-cache` capability keys entries on prompt text and rejects only blank completions, so a model that wraps its JSON in prose has that unparseable answer cached: every retry replays it from cache in milliseconds and the exponential backoff accomplishes nothing. Episode 202 burned three attempts at four milliseconds each without reaching the model, and a manual retry half an hour later did the same, so the episode could not be recovered by retrying at all.

If no attempt yields either a strict parse or an acceptable salvage, the error SHALL propagate: the system SHALL NOT silently fall back to composing un-deduped articles. During episode generation a propagated dedup failure SHALL fail the episode (status `FAILED`, retryable). During preview a propagated dedup failure SHALL be surfaced as an error to the caller. The error SHALL describe the response as unparseable rather than as truncated, because an off-schema response reaches the same branch.

#### Scenario: Dedup requests no reasoning
- **WHEN** the dedup request is built for an `openrouter` model
- **THEN** the extra body's `reasoning` object carries `effort` `none` and `exclude`, rather than the block being omitted

#### Scenario: Response wrapped in prose and a code fence is parsed
- **WHEN** the dedup response is complete JSON preceded by a `**Output:**` lead-in and wrapped in a ```json fence
- **THEN** the clusters are parsed and the episode is composed from them

#### Scenario: Chatter after the JSON is ignored
- **WHEN** the dedup response is followed by a sign-off containing a stray bracket
- **THEN** the clusters are parsed and the sign-off is ignored

#### Scenario: Bare cluster array is parsed
- **WHEN** the dedup response is a bare array of cluster objects rather than the asked-for `{ "clusters": [...] }` object
- **THEN** the array is read as the cluster list

#### Scenario: Truncated bare array is salvaged
- **WHEN** a bare cluster array is cut off part-way through a later element
- **THEN** the complete elements before the cut are recovered

#### Scenario: Truncated response with enough clusters is salvaged
- **WHEN** the dedup response is cut off mid-array after 234 complete clusters that together select at least `app.compose.max-articles` articles
- **THEN** the 234 clusters are used, a WARN records the salvage, and the episode is composed from them

#### Scenario: Truncated response with too few clusters is retried
- **WHEN** the dedup response is cut off after 3 complete clusters selecting 5 articles and `app.compose.max-articles` is 40
- **THEN** the attempt fails and the retry applies rather than composing from 5 articles

#### Scenario: Articles beyond the truncation point are not composed
- **WHEN** a salvaged response's surviving clusters mention only some of the candidate articles
- **THEN** the unmentioned candidates are not selected for composition

#### Scenario: First attempt sends the prompt unchanged
- **WHEN** the dedup call's first attempt is made
- **THEN** the prompt carries no correction text

#### Scenario: Retry appends a JSON-only correction
- **WHEN** the dedup call's first attempt fails and a second is made
- **THEN** the second prompt is the original prompt followed by a correction naming attempt 2 and asking for the raw clusters object only

#### Scenario: Every attempt sends a distinct prompt
- **WHEN** the dedup call fails on all attempts
- **THEN** the prompts sent are all different from one another, so no attempt can be served a cached response from an earlier attempt

#### Scenario: Dedup failure fails the episode
- **WHEN** the dedup LLM call yields neither a strict parse nor an acceptable salvage on every retry during episode generation
- **THEN** the episode is marked FAILED and is NOT composed with un-deduped articles

#### Scenario: Dedup failure surfaces during preview
- **WHEN** the dedup LLM call fails on every retry during a preview
- **THEN** the preview reports an error rather than returning an un-deduped script

#### Scenario: Empty filtered result still skips the episode (not an error)
- **WHEN** dedup succeeds but every cluster selects no articles (all continuations with no new developments)
- **THEN** the pipeline skips briefing generation for that run without raising an error
