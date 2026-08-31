## ADDED Requirements

### Requirement: LLM request timeouts are set per pipeline stage
The system SHALL apply a request timeout to each LLM call based on the pipeline stage the model was resolved for, rather than one value shared by every stage. `ResolvedModel` SHALL carry its `PipelineStage`, and `ChatClientFactory` SHALL select the timeout from it.

Timeouts SHALL be configurable under `app.llm.timeouts`, defaulting to 3 minutes for `filter`, 5 minutes for `dedup` and 20 minutes for `compose`. The recap stage resolves the filter model and SHALL therefore take the filter timeout.

The values reflect measured behaviour. Per-article scoring returns in seconds and dedup has run to a maximum of 3 minutes 6 seconds, so neither needs more than a few minutes. Composition, however, has been observed between 1 minute 3 seconds and 18 minutes 11 seconds when working over a large article set with research and history tool calls, so its ceiling SHALL remain generous: a 15-minute ceiling would have failed a real episode.

A single blanket 20-minute timeout previously let one hung scoring call stall an entire generation for 13 minutes while the other 177 articles had each returned in seconds.

#### Scenario: A hung scoring call is cut off in minutes, not tens of minutes
- **WHEN** an article-scoring request stops responding
- **THEN** it fails after the filter timeout (3 minutes by default), not after the compose timeout

#### Scenario: A long composition is not cut off
- **WHEN** a compose request with research and history tool calls runs for 18 minutes
- **THEN** it is allowed to complete, because the compose timeout is 20 minutes

#### Scenario: Dedup takes the dedup timeout
- **WHEN** the dedup stage issues its LLM call
- **THEN** the request timeout is the configured dedup value, independent of the compose value

#### Scenario: Recap takes the filter timeout
- **WHEN** the recap stage issues its LLM call, having resolved the filter model
- **THEN** the request timeout is the configured filter value

### Requirement: Compose bounds its output tokens
The composers (`BriefingComposer`, `DialogueComposer`, `InterviewComposer`) SHALL set a `maxTokens` ceiling on the compose request, configured as `app.compose.max-output-tokens` (default 96000).

Without a ceiling the provider reserves the model's entire output window when checking affordability, which blocked episode 191 with a 402 demanding credit for 131,072 tokens to produce a 1,876-word script. The ceiling SHALL leave ample headroom over real usage: compose output across 59 episodes ranged from 2,540 to 57,546 tokens, the upper end reflecting a reasoning model whose output greatly exceeds the script it returns. A ceiling below observed usage would truncate a script, which is why the default sits well above the maximum seen rather than close to it.

#### Scenario: Compose request declares an output ceiling
- **WHEN** any composer issues its compose request
- **THEN** the request carries `maxTokens` equal to `app.compose.max-output-tokens`

#### Scenario: Ceiling leaves headroom over observed usage
- **WHEN** the default ceiling is applied
- **THEN** it exceeds the largest observed compose output (57,546 tokens) by a wide margin and stays below the model's 131,072-token window
