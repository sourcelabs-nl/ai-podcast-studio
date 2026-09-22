## Context

Compose is the most expensive and slowest pipeline stage. It registers `searchPastEpisodes` always and `webSearch` for deep-dive podcasts and focus episodes. Spring AI's tool loop answers a tool call by re-sending the whole conversation, so any call costs a second full compose round: on episode 227 round 1 took 126s, the tools about 1s, and round 2 took 1045s, with 67-90% of compose output tokens spent on reasoning. What the model searches for is predictable from the dedup topic clusters (or the focus text), so the searches can run before compose.

Measured side issues in the same area: `llm_calls` does not say which upstream provider OpenRouter picked or how many tokens were reasoning; the persisted compose tokens only reflected the last request; and `RoleTagValidationAdvisor` warns about discarded `|||TOPIC_ORDER|||` text on every multi-speaker script.

## Goals / Non-Goals

**Goals:**
- Compose is a single model call on every path (speaker-tag validation retries excepted).
- The compose prompt keeps the information the tools provided: background research and prior coverage, with the same semantics.
- The plan call, searches and sources are observable and costed.
- Served provider and reasoning tokens are recorded per request.

**Non-Goals:**
- Changing the dedup stage, the focus selection, or the Tavily client and cache.
- Frontend display of the new request fields (the API carries them).
- Changing how cached Tavily searches are charged (each search still counts as one research call).

## Decisions

### Research runs inside `LlmPipeline`, before the composer

`LlmPipeline.compose`, `recompose` and `preview` are the chokepoints every compose path already passes through (generation, retry, regeneration, focus, feedback recompose, preview). Each calls `PreComposeResearchService.research(...)` with the podcast, the compose articles and the `ComposeContext`, and copies the result into `ComposeContext.research` before calling the composer. It runs outside the `compose` Resilience4j retry, so a transient compose fault does not repeat the research. Wiring it in `PodcastService` instead would have meant six call sites.

Alternative considered: a separate pipeline step persisted on the episode between dedup and compose. Rejected: it adds a resume point and persisted state for data that is cheap to recompute from the caches.

### Subjects

The research subjects are the focus text (focus episode) followed by the context's topic labels; when both are missing (an old episode without labels), the titles of the compose articles. Distinct, blank ones dropped.

### Plan call

`ResearchPlanner` makes one call through `ChatClientFactory.createForModel` on the podcast's filter model (`ModelResolver.resolve(podcast, FILTER)`), temperature 0.3, no reasoning, a small `maxTokens` cap, parsed with `BeanOutputConverter(ResearchQueryPlan, jsonMapper)` using the injected `JsonMapper`. It records under its own telemetry stage: `ResolvedModel` gains `telemetryStage` (default `stage.value`), which `CachingChatModel` records instead of `stage.value`, so the plan is `research-plan` while keeping the filter stage's timeout and pricing. `ReportedStage.all` lists `research-plan` against the filter timeout.

It keeps a hand-rolled parse rather than `validateSchema()` (SA3), because it has a salvage rule: any failure (unparseable, empty, provider error) falls back to the first N subjects as queries, which is always usable. A retry would buy a nicer query at the price of a second call and latency, and must never fail the episode. The plan call uses the LLM cache even on an evaluation run, so repetitions of a compose variant share identical research.

N is 3 for a deep-dive regular episode (`RESEARCH_QUERY_CAP`) and 5 for a focus episode (`FOCUS_RESEARCH_QUERY_CAP`). No plan call and no search is made when web search does not apply.

### Web search

Queries run concurrently in a `coroutineScope` on `Dispatchers.IO`, bounded by a `Semaphore(3)`, each through `ResearchService.search(userId, query, 5)`, which already caches and returns empty on Tavily failure; any other exception is logged and yields no results (cancellation is rethrown). `researchCalls` is the number of queries searched, and `researchCostCents` is computed from it as before.

Sources are recorded by `ResearchSourceRecorder.replace(episodeId, sources)`, `@Transactional`, deleting the episode's previous sources first. This makes a retry or a feedback recompose idempotent and replaces the explicit `clearResearchSources` call in the feedback recompose. Sources are recorded for regular episodes too. A preview (no episode id) records nothing.

### History

`EpisodeHistoryRepository.search` runs for the first 5 subjects with limit 5 each, keeping each episode once in first-seen order (`HISTORY_SUBJECT_CAP`, `HISTORY_MATCHES_PER_SUBJECT`). It runs for every episode. `PastEpisodeMatch` is passed to the prompt as-is, including `isFocusEpisode`.

### Prompt

The composers build their client with `createForModel`, so no tools are registered. `ChatClientFactory.createForCompose`, `buildComposeTools`, `HistoryLookupTool`, `ResearchTool` and `ToolBudget` are removed.

Two data blocks follow the article summaries: "Background research" (query, title, URL, snippet per source) and "Previously covered" (date, topics, recap snippet, a focus-episode marker). The engagement bullets keep "WHAT COUNTS AS NEW", and replace the tool instructions with guidance on the blocks: history is for framing and wording, never grounds to skip or demote; a focus-episode match is a continuation; background research is woven into the standout stories with attribution (full segments only when subtopics apply; for a focus episode, across the one subject). The recent-focus-episodes instruction and the audience bullet refer to the blocks rather than to tools. Without research the web-research bullet and block are absent; without matches the history block is absent.

### Feedback recompose reruns research

The simplest correct choice: rerunning is automatic because `LlmPipeline.compose` always researches, the planning prompt is identical so the plan is an LLM-cache hit, and identical Tavily queries are cache hits. Reusing stored sources instead would need snippets that `episode_research_sources` does not hold.

### Telemetry fields

V80 adds nullable `served_provider TEXT` and `reasoning_tokens INTEGER` to `llm_calls`. `TokenUsage` gains `reasoningTokens`, read from the native `CompletionUsage.completionTokensDetails().reasoningTokens()`. The served provider is read from the response metadata key `provider`, which Spring AI's `OpenAiChatModel` copies from the completion's additional properties. Both degrade to null on any shape failure. `LlmCallRecord`, `LlmCall`, `LlmCallRow` and `LlmCallResponse` carry them; `requestsForEpisode` selects them.

### Compose totals

`RoleTagValidationAdvisor` keeps the usage of each response it discards before re-issuing, and the dialogue and interview composers add it to the final response's usage (`TokenUsage.plus`). A reported cost is summed only when every part reported one; otherwise the sum carries none and the stage is costed from rates. With no tool rounds, the final response is otherwise the whole stage.

### Plan cost

The plan's cost is read from its `llm_calls` rows (the cost projection already groups by stage) and added to the research row of the cost breakdown; `episodes.research_cost_cents` stays the Tavily cost. No new episode columns: the plan costs fractions of a cent, and a persisted integer would round it to zero.

### Topic-order warning

The advisor validates `cleanUpComposedScript(TopicOrderExtractor.extract(text).script, roles)`, the same order the composers use.

## Risks / Trade-offs

- [The model can no longer ask a follow-up question the plan did not anticipate] → The plan sees every cluster title and the focus; measured tool use was 1-3 calls on the same stories. Accepted for a 10x compose latency win.
- [Prompt grows by the research and history blocks] → Bounded: at most 5 queries x 5 results and 25 history matches with 280-character snippets.
- [Regular deep-dive episodes now record sources, and a keyword-matched history block is always present] → The history guidance keeps the rule that a match never demotes a story, which is the failure the tool-era prompt was written against.
- [Plan fallback searches raw cluster titles] → Tavily handles plain titles well; the fallback only triggers on a failed plan.

## Migration Plan

V80 is additive (nullable columns). Rollback is a code revert; the columns can stay.

## Open Questions

None.
