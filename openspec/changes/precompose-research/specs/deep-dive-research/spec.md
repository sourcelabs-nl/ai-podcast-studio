## MODIFIED Requirements

### Requirement: Opt-in deep-dive research per podcast

A podcast SHALL have a boolean `deepDiveEnabled` field (default `false`). When `true`, the pre-compose research stage MUST run web searches for a regular episode; when `false`, it MUST NOT, except for a focus episode, which SHALL always run web searches regardless of `deepDiveEnabled`. The compose stage itself SHALL register no `webSearch` tool in either case.

#### Scenario: Disabled podcast does not register the tool
- **WHEN** a regular episode is composed for a podcast with `deepDiveEnabled=false`
- **THEN** no web search is made, no `webSearch` tool is registered, and the compose prompt has no background research block

#### Scenario: Enabled podcast registers the tool
- **WHEN** a regular episode is composed for a podcast with `deepDiveEnabled=true`
- **THEN** web searches run before compose, their results appear in the compose prompt, and no `webSearch` tool is registered

#### Scenario: Focus episode researches regardless of the podcast setting
- **WHEN** a focus episode is composed for a podcast with `deepDiveEnabled=false`
- **THEN** web searches run before compose

### Requirement: Per-episode research budget

The research stage SHALL search at most 3 queries per regular episode and at most 5 per focus episode. Planned queries beyond the applicable cap MUST be dropped rather than searched.

#### Scenario: Budget exhausts gracefully
- **WHEN** the plan for a regular episode proposes 4 queries
- **THEN** 3 web searches are made

#### Scenario: Focus episode cap
- **WHEN** the plan for a focus episode proposes 6 queries
- **THEN** 5 web searches are made

## REMOVED Requirements

### Requirement: Tavily-backed webSearch tool

**Reason**: Web search no longer runs as a compose tool; every tool call forced a second, much slower compose round.
**Migration**: The pre-compose research stage (`precompose-research`) runs the same cached Tavily search with the same 5-result, empty-on-failure behavior and hands the results to the compose prompt.

### Requirement: Compose prompt nudges deep-dive usage

**Reason**: The model no longer decides when to search.
**Migration**: The compose prompt carries a "Background research" block and tells the model how to use it (`precompose-research`).
