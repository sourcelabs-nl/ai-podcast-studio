## ADDED Requirements

### Requirement: Opt-in deep-dive research per podcast

A podcast SHALL have a boolean `deepDiveEnabled` field (default `false`). When `true`, the compose stage MUST register a `webSearch` tool with the LLM; when `false`, no `webSearch` tool MUST be registered.

#### Scenario: Disabled podcast does not register the tool

- **WHEN** a podcast has `deepDiveEnabled=false` and the compose stage runs
- **THEN** the LLM `ChatClient` has no `webSearch` tool registered

#### Scenario: Enabled podcast registers the tool

- **WHEN** a podcast has `deepDiveEnabled=true` and the compose stage runs
- **THEN** the LLM `ChatClient` has a `webSearch` tool registered

### Requirement: Tavily-backed webSearch tool

The `webSearch` tool SHALL take a single string parameter `query` and SHALL return up to 5 results, each containing `title`, `url`, and `snippet`. The implementation SHALL call the Tavily API with the user's configured Tavily key (falling back to the `TAVILY_API_KEY` environment variable). Tool errors and timeouts (10-second timeout) MUST NOT fail episode generation; they MUST return an empty result set with a warning logged.

#### Scenario: Successful Tavily call returns snippets

- **WHEN** the LLM invokes `webSearch("agentic AI security")` and Tavily returns 3 results
- **THEN** the tool returns a list of 3 search snippet objects with `title`, `url`, `snippet`

#### Scenario: Tavily timeout does not break the pipeline

- **WHEN** the Tavily request exceeds 10 seconds
- **THEN** the tool returns an empty list and the compose stage continues

#### Scenario: Missing Tavily key short-circuits

- **WHEN** the podcast has `deepDiveEnabled=true` but no Tavily key is configured for the user and no env fallback is set
- **THEN** the tool returns an empty list and a warning is logged once per episode

### Requirement: Per-episode research budget

The compose stage SHALL enforce a maximum of 3 `webSearch` invocations per episode generation. Calls beyond the cap MUST return an empty result set with a `budgetExhausted=true` indicator and MUST NOT throw.

#### Scenario: Budget exhausts gracefully

- **WHEN** the LLM has already invoked `webSearch` 3 times in a single compose run and attempts a 4th call
- **THEN** the tool returns an empty list with `budgetExhausted=true`

### Requirement: Tavily response caching

Tavily responses SHALL be cached keyed on `(query, max_results)`. A cache hit MUST be served without an outbound Tavily call.

#### Scenario: Identical query hits the cache

- **WHEN** the same query is issued by `webSearch` twice
- **THEN** the second invocation does not perform an outbound HTTP request to Tavily

### Requirement: Compose prompt nudges deep-dive usage

When `podcast.deepDiveEnabled=true`, the compose-stage prompts (monologue, dialogue, interview) SHALL include instructions directing the LLM to identify the single most newsworthy story and call `webSearch` 1–2 times for outside context before writing that story's segment. When `deepDiveEnabled=false`, the prompts MUST NOT reference `webSearch`.

#### Scenario: Enabled podcast prompt references webSearch

- **WHEN** the compose prompt is built for a podcast with `deepDiveEnabled=true`
- **THEN** the prompt text references `webSearch` and instructs how to use it

#### Scenario: Disabled podcast prompt omits webSearch

- **WHEN** the compose prompt is built for a podcast with `deepDiveEnabled=false`
- **THEN** the prompt text does not reference `webSearch`
