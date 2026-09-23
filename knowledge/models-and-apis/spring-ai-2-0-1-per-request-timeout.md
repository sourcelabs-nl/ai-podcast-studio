---
okf_version: "0.2"
type: finding
title: Spring AI 2.0.1 caps every OpenAI request at 60 seconds unless the call options carry a timeout
answers: LLM requests fail at exactly 60s with "Error reading response" / InterruptedIOException timeout, or when upgrading Spring AI
status: stable
generated:
  by: claude-opus-5-5
  at: 2026-09-23T00:00:00Z
method: >
  Observed on 2026-09-23 after upgrading Spring AI 2.0.0 to 2.0.1: every compose
  request longer than a minute failed at 60,003-60,010 ms (request telemetry of
  experiment episodes 231-251), while the same requests on 2.0.0 ran to 248s and
  failed only at the configured 20m. Traced in the 2.0.0 and 2.0.1 sources jars
  and openai-java 4.39.1: OpenAiChatModel.buildRequestOptions,
  AbstractOpenAiOptions.DEFAULT_TIMEOUT, RequestOptions.applyDefaults.
model_version: spring-ai 2.0.1 with com.openai:openai-java-client-okhttp 4.39.1
stale_after: 2027-03-23T00:00:00Z
---

# Spring AI 2.0.1 caps every OpenAI request at 60 seconds unless the call options carry a timeout

Spring AI 2.0.1 added `OpenAiChatModel.buildRequestOptions`, which sends the chat
options' timeout as a per-request `RequestOptions.timeout` on every call. An
`OpenAiChatOptions` built without `.timeout(...)` does not report null: it falls
back to `AbstractOpenAiOptions.DEFAULT_TIMEOUT`, 60 seconds. In openai-java the
per-request value is merged over the client's own `Timeout` and wins, and the read
timeout falls back to it, so the timeout the `OpenAIClient` was built with no
longer applies to anything. 2.0.0 had no per-request override, which is why the
client-level timeout worked until the upgrade.

The failure reads as a provider problem: `OpenAIInvalidDataException: Error
reading response` caused by `InterruptedIOException: timeout`, at a suspiciously
round 60 seconds. Short requests still succeed, so the regression surfaces only
on the longest stage and looks intermittent. Nothing in the upgrade's test suite
caught it, since no unit test waits a minute.

The fix is to put the stage's timeout on the call options. Here that happens in
`withRoutingAndReasoning(ResolvedModel)`, which every stage's options go through,
from `ResolvedModel.requestTimeout` filled by `ModelResolver`. The client-level
timeout in `buildOpenAiClient` stays as a fallback for anything built outside it.

Any experiment run on 2.0.1 before the fix was capped at 60 seconds per request,
which fails long compose requests and biases the surviving runs toward short,
low-reasoning ones: [[pipeline-experiments-2026-09]] discards those runs.
