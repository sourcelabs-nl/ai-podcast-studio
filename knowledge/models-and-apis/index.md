---
okf_version: "0.2"
type: index
title: Models, SDKs and APIs
status: stable
generated:
  by: claude-opus-5-5
  at: 2026-09-23T00:00:00Z
---

# Models, SDKs and APIs

Measured behaviour of the models, SDKs, drivers and APIs this project depends on,
including the formats of the feeds it reads. This differs from
[`references/`](../references/index.md), which holds external material we learn
from. Each finding states how it was measured and expires, because a vendor can
change behaviour behind an unchanged name.

- [How OpenRouter requests are routed: the quantization floor, require_parameters and provider sort](openrouter-routing.md)  
  Read when an OpenRouter call returns 404 "No endpoints found", lands on an unexpected provider, or when adding a model or a provider preference.
- [What a Narro RSS feed carries, and what it does not](narro-feed-format.md)  
  Read when changing how threads are reconstructed from Narro-sourced X content.
- [What Jev can decide for this pipeline, and what it cannot](jev-decisions-endpoint.md)  
  Read when deciding where Jev can carry a call in this pipeline and where it cannot.
- [Jev as a script judge, measured against this project's own judge](jev-as-a-judge.md)  
  Read when considering Jev in place of, or alongside, ScriptJudge.
- [The SQLite driver returns no generated keys from a batch](generated-keys-from-a-batch.md)  
  Read when a save of several new rows fails with "After saving the identifier must not be null".
- [OpenAI reasoning models reject temperature, and OpenRouter reports it as a routing failure](openai-reasoning-models-reject-temperature.md)  
  Read when an openai/ model returns 404 "No endpoints found that can handle the requested parameters".
- [Spring AI 2.0.1 caps every OpenAI request at 60 seconds unless the call options carry a timeout](spring-ai-2-0-1-per-request-timeout.md)  
  Read when LLM requests fail at exactly 60s with "Error reading response" / InterruptedIOException timeout, or when upgrading Spring AI.
