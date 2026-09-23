# precompose-research Specification

## Purpose

Gathers the outside context and the prior coverage a script needs before compose runs, so the compose stage receives it in its prompt and completes in a single model call.

## Requirements

### Requirement: Research runs before compose on every compose path

Every compose path (generation, retry, regeneration, focus episode, feedback recompose and preview) SHALL run the research stage before the composer is called and SHALL pass its result to the compose prompt. The research subjects SHALL be the focus text of a focus episode followed by the episode's topic cluster titles; when there are none, the titles of the compose articles SHALL be used.

#### Scenario: Regular generation researches the dedup clusters
- **WHEN** a regular episode is composed after dedup produced the topic labels "OpenAI o5 launch" and "EU AI Act fines"
- **THEN** the research stage runs for those two subjects before the compose call

#### Scenario: Focus episode researches its focus
- **WHEN** a focus episode with focus "Claude Opus 5.5" is composed
- **THEN** "Claude Opus 5.5" is the first research subject

### Requirement: A planning call turns subjects into web search queries

When web search applies to the run, the research stage SHALL make one call on the podcast's filter-stage model that receives the research subjects and returns at most N web search queries as structured output, where N is 3 for a regular episode of a podcast with `deepDiveEnabled=true` and 5 for a focus episode. Queries beyond N SHALL be dropped. The call SHALL be recorded as an LLM request under the stage `research-plan`, attributed to the episode. A planning response that cannot be parsed or is empty MUST NOT fail the episode: the first N subjects SHALL be used as the queries instead.

#### Scenario: Plan returns queries within the cap
- **WHEN** a regular deep-dive episode's plan call returns five queries
- **THEN** only the first three are searched

#### Scenario: Unparseable plan falls back to the subjects
- **WHEN** the plan call returns text that is not the expected JSON
- **THEN** the first N research subjects are searched as queries and the episode continues

#### Scenario: Plan call is recorded under its own stage
- **WHEN** the plan call completes for episode 230
- **THEN** a request with stage `research-plan` and episode 230 is recorded

### Requirement: Web search applies only when research is enabled

Web search SHALL run for a focus episode, and for a regular episode only when the podcast has `deepDiveEnabled=true`. When it does not apply, no planning call and no web search SHALL be made.

#### Scenario: Regular podcast without deep dive
- **WHEN** a regular episode is composed for a podcast with `deepDiveEnabled=false`
- **THEN** no `research-plan` request and no Tavily search is made, and the prompt has no background research block

### Requirement: Web searches run concurrently and never fail the episode

The planned queries SHALL be searched through the cached Tavily search, at most 5 results each, concurrently with a bounded concurrency. A search that errors or times out SHALL contribute no results and MUST NOT fail the episode. Each search counts as one research call for the episode's research cost.

#### Scenario: One search fails
- **WHEN** three queries are searched and the second one times out
- **THEN** the results of the first and third are used, and the episode's research calls are 3

### Requirement: Research sources are recorded per episode

Every result of the research stage's web searches SHALL be recorded against the episode being composed, with the query that found it, its title and URL, in result order. A new run of the research stage for an episode SHALL replace the episode's previously recorded sources. A run without an episode (a preview) SHALL record nothing.

#### Scenario: Retry does not duplicate sources
- **WHEN** an episode's compose is retried and the research stage runs again
- **THEN** the episode holds only the sources of the latest run

### Requirement: Past coverage is looked up up front

The research stage SHALL search the podcast's past episodes for each research subject, at most 5 subjects and 5 matches per subject, keeping each episode once, and SHALL carry for each match its date, topics, recap snippet and whether it was a focus episode. This lookup SHALL run for every episode, whether or not web search applies.

#### Scenario: Regular podcast still gets history
- **WHEN** a regular episode is composed for a podcast with `deepDiveEnabled=false` and a past episode matches a subject
- **THEN** the compose prompt contains a previously covered block listing that episode

### Requirement: Compose receives research in its prompt and uses no tools

The compose prompt SHALL contain a "Background research" block listing each web search result's title, URL and snippet when there are results, and a "Previously covered" block listing the past-episode matches when there are any. The compose call SHALL register no tools, so it completes in a single model request apart from speaker-tag validation retries. The prompt SHALL tell the model that the `[FOLLOW-UP: ...]` headers remain authoritative for what is new, that a history match is for framing and wording and not evidence the story was covered, that a focus-episode match is a continuation to build on, and that background research is attributed and used for the standout stories (full segments only when subtopics apply).

#### Scenario: No tool round
- **WHEN** any episode is composed
- **THEN** the compose request carries no tool definitions and the stage records one compose request

#### Scenario: Focus match framed as continuation
- **WHEN** a previously covered match is a focus episode
- **THEN** it is marked as a focus episode in the block and the prompt says to treat it as a continuation

### Requirement: Feedback recompose reruns research from the caches

A focus episode's feedback recompose SHALL run the research stage again. Because the planning prompt and the Tavily queries are unchanged, both SHALL be served from their caches, so the recomposed script sees the same research without new charges.

#### Scenario: Recompose with feedback
- **WHEN** a reviewer submits feedback on a focus episode
- **THEN** the research stage runs again and its plan request is a cache hit
