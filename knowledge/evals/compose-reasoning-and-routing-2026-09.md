---
okf_version: "0.2"
type: finding
title: Compose spends most of its output budget on reasoning, and default routing served it from a slower DeepInfra endpoint on episodes 220-228
answers: deciding whether to lower compose reasoning effort, add throughput-based OpenRouter provider routing, or diagnosing why a compose call ran long or cost more than expected
status: stable
model_version: deepseek/deepseek-v4.1-flash via OpenRouter, reasoning effort medium, exclude:true
method: >
  Raw data pulled per episode via GET /llm/calls/episodes/{id} for episodes
  220-228. Script tokens are estimated as chars/4; output and reasoning tokens
  are the values OpenRouter reported in llm_calls. Provider was inferred from
  cost matching DeepInfra's published price ($0.14/$0.42 per M) because
  llm_calls did not yet store a served provider at the time; it does as of
  commit cb9e14b, alongside reasoning_tokens. Endpoint throughput figures are
  read from the OpenRouter endpoints API, not measured directly. The compose
  timeout and the tool-calling round-trip figures come from episode 227's call
  log under the two-call compose flow; the precompose-research OpenSpec change
  moved history lookup and web search ahead of compose into a separate
  research-plan call, and episode 228's focus recompose is measured under that
  new flow.
stale_after: 2026-12-22T00:00:00Z
generated:
  by: claude-sonnet-5
  at: 2026-09-22T00:00:00Z
---

**Reasoning is most of the output.** Across episodes 220-227, 67-90% of compose
output tokens were reasoning tokens rather than script text. Episode 227: 31,442
output tokens against a script of 1,819 words. This is with reasoning effort set
to medium and `exclude: true`, so none of that reasoning is visible or usable,
only billed and waited on.

**Default routing served compose from the slower endpoint on episodes 220-228.**
With price-weighted routing and this project's fp8 quantization floor, cost matched DeepInfra's
$0.14/$0.42 per M, which is the cheapest endpoint that passes the floor.
DeepInfra's own p50 throughput is 46 tok/s; measured compose throughput was
26-51 tok/s. Endpoints that pass the same quantization floor and are faster,
per the OpenRouter endpoints API: CoreWeave fp8 at 162 tok/s p50 ($0.65/M out),
Parasail at 147, AtlasCloud at 136, Novita at 124. BaseTen was ruled out on a
hard limit, not price: its 32k max completion tokens is below this project's
configured max-output-tokens of 96000. The endpoint the price order lands on moves
with the provider mix: on 2026-09-23 every DeepSeek compose run, sorted by
throughput or not, was served by Novita. See [[openrouter-routing]]. OpenRouter's `provider.sort: "throughput"`
and `preferred_min_throughput` combine with `quantizations` and
`require_parameters` to express a throughput-first route.

**Tool calls added a second full-price round trip.** Under the old flow,
episode 227's first round (searchPastEpisodes x2, webSearch x2) took 126
seconds wall clock, but the tool calls themselves were about 1 second combined;
the second round, which re-sent the full conversation, took 1045 seconds against
23,893 input tokens (mostly cache-read). Moving history lookup and web search
ahead of compose into their own research-plan call removes that second round
entirely: episode 228's focus recompose ran as a single tool-free compose call
that took 106.5 seconds with 2,994 reasoning tokens (provider DeepInfra), plus a
1.8-second research-plan call, for a 121-second total recompose.

**The compose timeout is a wall-clock ceiling, not an idle timeout.** callTimeout
is 10 minutes; it was 20 while compose ran its own tool calls. Because a non-streamed response sends no bytes until it
completes, an okhttp readTimeout on that connection would not behave as an idle
timeout unless compose were switched to streaming.

**A related false alarm.** The "Discarded ... untagged text |||TOPIC_ORDER|||"
warning seen around this period was not a real defect: the advisor step already
cleaned the text before topic-order extraction ran. Fixed in commit cb9e14b.

**Effect on script quality.** Measured in [[pipeline-experiments-2026-09]]:
reasoning effort none to high and throughput sort all scored within run-to-run
spread on the judge. That is one article set of one podcast, so it does not yet
justify changing the compose default.

Related: [[pipeline-experiments-2026-09]], [[spring-ai-2-0-1-per-request-timeout]]
