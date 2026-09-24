---
okf_version: "0.2"
type: finding
title: OpenRouter's generation stats split a request into startup, reasoning and writing, but its "latency" is not time to first token
answers: a single LLM request was slow and you need to know whether it queued, reasoned, generated slowly or fell back between providers; or when reading OpenRouter's /api/v1/generation fields
status: draft
generated:
  by: claude-opus-5-5
  at: 2026-09-24T00:00:00Z
method: >
  Live GET /api/v1/generation?id=gen-… lookups on 2026-09-24: once on a small
  probe request (deepseek/deepseek-v4.1-flash, served by Together), and once on
  the compose request of experiment episode 276 (episode 230's article set,
  deepseek/deepseek-v4.1-flash served by Venice, 160,582 ms measured by our own
  client), comparing each timing field against our measured request duration and
  the token counts.
model_version: deepseek/deepseek-v4.1-flash-20260910 (OpenRouter, 2026-09-24)
stale_after: 2027-03-24T00:00:00Z
---

# OpenRouter's generation stats split a request into startup, reasoning and writing

**The stats are not available right away.** The endpoint answers 404 "Generation
… not found" immediately after the request and returns the stats 10-20 seconds
later, so a lookup has to wait and retry.

**What the timing fields measure**, from the episode 276 compose request:

| field | value | meaning |
|---|---|---|
| `generation_time` | 159,954 ms | the whole request (our client measured 160,582 ms) |
| `latency` | 130,538 ms | time until the first **answer** token, after reasoning |
| `provider_responses[].latency` | 1,777 ms | time until that endpoint started responding |

`latency` is not time to first token: on a reasoning model it includes all the
reasoning (10,637 of the request's 14,020 native completion tokens here). The
startup wait is the sum of the attempt latencies in `provider_responses`, which
also lists every endpoint OpenRouter tried and its HTTP status, so a fallback
after a failing provider is visible there and nowhere else.

That request therefore spent about 1.8 s starting, 129 s reasoning and 29 s writing
the script, at about 89 native tokens per second. Reasoning dominated. This matches
[[compose-reasoning-and-routing-2026-09]], which found 67-90% of compose output was
reasoning.

**Native token counts differ from the normalised usage.** The probe reported 117
native completion tokens against 153 normalised (`tokens_completion`). Throughput
has to be computed from the native counts, which are the provider's own.

These values are stored per request on `llm_calls` and shown as startup, reasoning
and writing phases in the episode's latency tab and in the experiment comparison
(OpenSpec change `openrouter-generation-stats`). Requests from before that change
carry no generation id and cannot be looked up after the fact.

This `latency` field is a response value describing what already happened, not
the same thing as the `provider.sort: "latency"` request option: see
[[openrouter-routing]].
