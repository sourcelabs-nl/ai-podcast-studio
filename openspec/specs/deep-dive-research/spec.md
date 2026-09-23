# deep-dive-research Specification

## Purpose
TBD - created by archiving change add-deep-dive-research-tavily. Update Purpose after archive.
## Requirements
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

### Requirement: Tavily response caching

Tavily responses SHALL be cached keyed on `(query, max_results)`. A cache hit MUST be served without an outbound Tavily call.

#### Scenario: Identical query hits the cache

- **WHEN** the same query is issued by `webSearch` twice
- **THEN** the second invocation does not perform an outbound HTTP request to Tavily
