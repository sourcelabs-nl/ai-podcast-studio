---
okf_version: "0.2"
type: finding
title: OpenAI reasoning models reject temperature, and OpenRouter reports it as a routing failure
answers: an openai/ model returns 404 "No endpoints found that can handle the requested parameters"
status: stable
generated:
  by: claude-opus-5-5
  at: 2026-09-23T00:00:00Z
method: >
  Live requests to OpenRouter chat completions for openai/gpt-6-luna with
  provider.require_parameters true, once with temperature 0.9 and once without,
  during the pipeline-runner Phase C live check on 2026-09-23. The first
  experiment run on Luna (episode 232) failed on it; the rerun without the
  parameter (episode 233) completed, served by OpenAI.
model_version: openai/gpt-6-luna (OpenRouter, 2026-09-23)
stale_after: 2027-03-23T00:00:00Z
---

# OpenAI reasoning models reject temperature, and OpenRouter reports it as a routing failure

`openai/gpt-6-luna` does not list `temperature` among its supported parameters.
Every request this project sends carries `provider.require_parameters: true`, so a
request with a temperature matches none of Luna's seven endpoints and OpenRouter
answers 404 "No endpoints found that can handle the requested parameters". The same
request without the temperature routes to OpenAI and completes.

The error names routing, not the parameter, and the same 404 is what the
quantization floor produced for vendor-served models, so the two causes look
identical from the response. Checking a model's `supported_parameters` on
`/api/v1/models` tells them apart; see [[openrouter-routing]].

Every stage here sets a temperature (0.3 for extraction, the podcast's own for
compose), so the fix is applied where the request options are finished rather than
at each call site: `withRoutingAndReasoning(ResolvedModel)` clears it for models
whose prefix `OpenRouterRouting.acceptsTemperature` rejects, currently `openai/`.
Open-weight models such as DeepSeek keep their temperature and an unchanged
request body.

Treating the whole `openai/` prefix as temperature-less is an assumption beyond
what was probed: only Luna was tested. A non-reasoning OpenAI model would lose a
parameter it accepts, which changes its sampling but does not fail the request.
