## MODIFIED Requirements

### Requirement: Opt-in deep-dive research per podcast

A podcast SHALL have a boolean `deepDiveEnabled` field (default `false`). When `true`, the compose stage MUST register a `webSearch` tool with the LLM; when `false`, no `webSearch` tool MUST be registered, EXCEPT for a focus episode, which SHALL always register the `webSearch` tool regardless of `deepDiveEnabled`.

#### Scenario: Disabled podcast does not register the tool
- **WHEN** a regular episode is composed for a podcast with `deepDiveEnabled=false`
- **THEN** the LLM `ChatClient` has no `webSearch` tool registered

#### Scenario: Enabled podcast registers the tool
- **WHEN** a regular episode is composed for a podcast with `deepDiveEnabled=true`
- **THEN** the LLM `ChatClient` has a `webSearch` tool registered

#### Scenario: Focus episode registers the tool regardless of the podcast setting
- **WHEN** a focus episode is composed for a podcast with `deepDiveEnabled=false`
- **THEN** the LLM `ChatClient` has a `webSearch` tool registered

### Requirement: Per-episode research budget

The compose stage SHALL enforce a maximum of 3 `webSearch` invocations per regular episode generation, and a maximum of 5 `webSearch` invocations per focus episode generation. Calls beyond the applicable cap MUST return an empty result set with a `budgetExhausted=true` indicator and MUST NOT throw.

#### Scenario: Budget exhausts gracefully for a regular episode
- **WHEN** the LLM has already invoked `webSearch` 3 times in a single regular-episode compose run and attempts a 4th call
- **THEN** the tool returns an empty list with `budgetExhausted=true`

#### Scenario: Budget exhausts gracefully for a focus episode
- **WHEN** the LLM has already invoked `webSearch` 5 times in a single focus-episode compose run and attempts a 6th call
- **THEN** the tool returns an empty list with `budgetExhausted=true`

#### Scenario: Budget exhausts gracefully
- **WHEN** the LLM has already invoked `webSearch` 3 times in a single episode generation and attempts a 4th call
- **THEN** the tool returns an empty list with `budgetExhausted=true`

## ADDED Requirements

### Requirement: Research sources are recorded per episode
Every `webSearch` invocation made while composing a focus episode SHALL be recorded against that episode: the query issued and the results it returned (each with `title` and `url`). The recorded sources SHALL be retrievable for display on the episode's review screen.

#### Scenario: Query and results recorded
- **WHEN** a focus episode's compose stage calls `webSearch("Claude Opus 5.5 benchmarks")` and receives 3 results
- **THEN** a research-source record is stored for that episode containing the query and the 3 results' titles and URLs

#### Scenario: Recorded sources are readable for review
- **WHEN** a focus episode reaches `PENDING_REVIEW`
- **THEN** its recorded research queries and sources can be retrieved and are included when the episode is shown for review
