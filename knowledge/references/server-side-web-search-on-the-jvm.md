---
okf_version: "0.2"
type: reference
title: Server-side web search, and why this project keeps Tavily
status: draft
method: >
  Desk research over the OpenAI Responses API docs, the OpenRouter web-search
  docs, the Spring AI issue tracker and the Koog sources. Nothing here was
  measured against a live endpoint from this project, so the capability claims
  are what the vendors document, not what we have observed.
stale_after: 2026-12-15T00:00:00Z
generated:
  by: claude-opus-5
  at: 2026-09-15T00:00:00Z
---

Web search reaches a model in two shapes, and the difference is who runs the
search loop.

**Client-side**, which is what this project does: the model emits a tool call,
the application performs the search, feeds the result back and issues a second
LLM call. Each search round is a round-trip through our own JVM. See
[`docs/deep-dive-research.md`](../../docs/deep-dive-research.md) for the
`webSearch` tool and the archived change `2026-05-21-add-deep-dive-research-tavily`
for why it was built that way.

**Server-side**: the provider performs the search inside one request. The model
can search and refine several times without the application seeing any of it.
OpenAI exposes this on `v1/responses` as `{"type": "web_search"}` and on
chat/completions as `web_search_options`; OpenRouter exposes it as
`{"type": "openrouter:web_search"}` on both, with an `engine` selector (native,
exa, firecrawl, parallel, perplexity), `max_results`, `max_uses`,
`max_total_results` and `allowed_domains`. `engine: native` delegates to the
underlying provider's own search where one exists and falls back to Exa
otherwise. Both shapes return `url_citation` annotations. OpenRouter's older
`plugins: [{"id": "web"}]` and the `:online` model suffix are deprecated.

**No JVM framework covers the Responses API in a typed way.** Spring AI has no
Responses support at all; it is planned for 2.1 with no date (issue 4221).
Chat/completions against OpenRouter works on a base-URL swap alone, but
`OpenAiChatOptions` has no field for the tool, so it has to be injected into the
request body by a `RestClient` interceptor. Koog goes further and still falls
short: its OpenAI client has a `responsesAPIPath` and `OpenAIResponsesParams`,
both marked experimental, and `OpenAIChatParams.webSearchOptions` is the
chat/completions variant rather than the Responses tools array; its OpenRouter
client has no responses path and nothing for server tools at all. Koog's tool
model assumes client-side tools that the framework itself executes, which server
tools do not fit.

**What the trade actually costs here.** Server-side search would take back four
things this pipeline holds deliberately: the hard cap of three calls per episode
(recoverable as `max_uses` on OpenRouter, absent on OpenAI native), the cache on
`(query_hash, max_results)` that makes an identical query free across episodes,
the per-user research key stored under category `RESEARCH` and its degradation to
empty results when absent, and a research cost we count ourselves rather than
reading back from `usage.server_tool_use.web_search_requests`. It would buy
fewer round-trips, a genuine multi-step search loop, and per-claim citations.

**Therefore Tavily stays.** The controls listed above are ones this pipeline
already tracks explicitly, and the framework gap means the swap would also cost a
hand-written interceptor. The condition that would reopen this: evidence that one
search round is not enough for the composer. That is a reason to build a
multi-step loop over Tavily first, since the loop and the server-side execution
are separable.
