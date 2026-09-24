---
okf_version: "0.2"
type: finding
title: "How OpenRouter requests are routed: the quantization floor, require_parameters and provider sort"
answers: an OpenRouter call returns 404 "No endpoints found", lands on an unexpected provider, or when adding a model or a provider preference
status: stable
generated:
  by: claude-opus-5-5
  at: 2026-09-23T00:00:00Z
method: >
  Read from OpenRouterRouting.extraBodyFor and withRoutingAndReasoning and the
  openrouter model comments in application.yaml, against the endpoint lists on
  OpenRouter's /api/v1/models and the live probes recorded in
  [[openai-reasoning-models-reject-temperature]] and
  [[pipeline-experiments-2026-09]] (2026-09-23).
model_version: OpenRouter chat completions provider routing, 2026-09-23
stale_after: 2026-12-23T00:00:00Z
---

# How OpenRouter requests are routed

Every OpenRouter call carries a `provider` block built by
`OpenRouterRouting.extraBodyFor`. A direct `openai` provider call gets none of it.

**The quantization floor.** `provider.quantizations` accepts fp8, fp16, bf16 and
fp32 and rejects everything else, fp4 and "unknown" included. Without it OpenRouter picks from every endpoint serving a model, and
those differ widely: `z-ai/glm-5.3` has 25 endpoints ranging fp4 to bf16. A lossy
endpoint follows the long, format-sensitive prompts worse (compose responses with
no speaker tags, a missing `finish_reason`, the wrong podcast name). It is a
quality bar rather than an allowlist, and `allow_fallbacks` stays at its default,
so an endpoint going down falls through to another that clears it.

**Closed-weight models are routed without the floor.** For the `openai/` and
`anthropic/` prefixes the floor is left out. Their endpoints are the vendor or a
cloud reselling the vendor's deployment, and all report quantization "unknown",
so the floor would reject every one: `openai/gpt-6-luna` has seven, all
"unknown". No third party can serve a lossy copy of a closed-weight model, so the
floor protects nothing there. `google/` is not on the list because it also
publishes open-weight Gemma models that third parties quantize.

**`require_parameters` is always sent**, so an endpoint that cannot honour what
the request carries (max tokens, the reasoning block, a temperature) is skipped
instead of silently ignoring it. The cost is its failure mode: when no endpoint
honours everything, OpenRouter answers 404 "No endpoints found that can handle
the requested parameters". That response names routing, not the parameter, and
the floor rejecting every endpoint produces a 404 that reads the same. Checking
the model's `supported_parameters` and endpoint quantizations on `/api/v1/models`
tells the causes apart. The temperature case is why `withRoutingAndReasoning`
clears the temperature for `openai/` models: see
[[openai-reasoning-models-reject-temperature]].

**Provider sort only reorders what the floor lets through.** A run can set
`provider.sort` (`price`, `throughput`, `latency`) and
`preferred_min_throughput`; neither widens the floor. This `latency` is a
request-side sort key, not the same thing as the `latency` field OpenRouter
returns from `/api/v1/generation`, which is time to the first answer token
after reasoning rather than a per-endpoint attempt time: see
[[openrouter-generation-stats]]. On 2026-09-23 every
DeepSeek compose run was served by Novita whether sorted by throughput or not,
because few endpoints clear the floor: see [[pipeline-experiments-2026-09]].
Which endpoint the default price order lands on moves with the provider mix:
episodes 220-228 were served by DeepInfra, per
[[compose-reasoning-and-routing-2026-09]].

**Reasoning travels in the same extra body**, as a `reasoning` object with an
explicit effort (including "none") and `exclude: true`, because OpenRouter does
not read the flat `reasoning_effort` field Spring AI emits.

Related: [[jev-decisions-endpoint]], whose chat arms sent this same routing body.
