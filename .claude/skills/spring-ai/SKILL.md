---
name: spring-ai
description: Spring AI rules for this project's LLM features (article scoring, topic dedup, script composition, recap generation, streaming vs .call(), structured extraction, per-stage model config, cost tracking). Consult BEFORE creating or changing any AI/LLM feature (ArticleScoreSummarizer, TopicDedupFilter, BriefingComposer, DialogueComposer, InterviewComposer, EpisodeRecapGenerator, ChatClientFactory, ModelResolver, CachingChatModel) and during review of files under com.aisummarypodcast.llm or anything using ChatModel/ChatClient/OpenAiChatOptions/BeanOutputConverter.
user-invocable: false
---

# Spring AI Rules

Rules for building and reviewing this project's LLM features. **Consult this skill before touching any Spring AI code** (`ChatModel`, `ChatClient`, `OpenAiChatOptions`, `BeanOutputConverter`, structured extraction, prompts, `ChatClientFactory`, `ModelResolver`, `CachingChatModel`, cost/usage tracking). All of it lives under `com.aisummarypodcast.llm`.

## Stack baseline

- **Spring AI 2.0.0** (Spring Boot 4). The chat model is built on the **official OpenAI Java SDK** (`OpenAiChatModel.builder().openAiClient(...)`), so the provider is any **OpenAI-compatible API**: OpenRouter (the default), OpenAI directly, or a local Ollama. The Spring AI OpenAI autoconfiguration is disabled (see `spring.autoconfigure.exclude` in `application.yaml`); models are constructed by hand in `ChatClientFactory`.
- Provider **credentials are resolved at runtime, per user**, via `UserProviderConfigService.resolveConfig(userId, ApiKeyCategory.LLM, provider)` and turned into an OpenAI SDK client by `buildOpenAiClient` (`OpenAiClientSupport.kt`, which also normalizes the base URL to include `/v1`). Never read an API key or base URL from anywhere else, and never hardcode one.
- Model + provider are **per-podcast and per-stage**: `podcast.llmModels[stage]` (filter / dedup / compose), falling back to `AppProperties.llm.defaults` (`StageDefaults`). Resolve through `ModelResolver.resolve(podcast, stage)` into a `ResolvedModel(provider, model, cost)`. Never hardcode a model id in a service; take the model from the resolved `ResolvedModel`.
- Every feature records token usage/cost. Any new LLM call MUST derive `TokenUsage.fromChatResponse(...)` and cost via `CostEstimator`, the same way existing services do (see `ArticleScoreSummarizer`, `EpisodeRecapGenerator`).

---

## Rule SA1: Prefer non-streaming `.call()` for anything parsed

Typed/structured extraction requires the **complete** response, so it must use `.call()`, never `.stream()`. Every service in this package already does this.

**Why this is a hard rule:** incremental JSON parsing on a streaming path is fragile — the first tokens that arrive are often not the JSON body (leading prose, a code fence, or a reasoning trace on reasoning-capable models), which breaks incremental parse and can trip client-side idle timeouts. `.call()` resolves the whole completion server-side first, leaving a single `content` string to parse.

- The only `.stream()` in the codebase is the `CachingChatModel.stream` delegate, which nothing on the parsing path uses.
- Use `.stream()` only for genuinely incremental, human-facing text output that is never parsed. We currently have no such feature.
- Never introduce `.stream()` for a structured-output feature (scoring, dedup, recap topic extraction). If you think you need it, you don't.

---

## Rule SA2: Reasoning is a per-feature cost/latency decision, not a default

This project has **no global reasoning toggle**. If you point a stage at a reasoning-capable model (e.g. a `claude-*` or `deepseek-*-pro` model via OpenRouter), decide reasoning **on task merit** and mind the two real costs on an OpenAI-compatible API, independent of parsing:

1. Reasoning tokens are billed and count against the response budget, so a tight `maxTokens` (e.g. the `DEDUP_MAX_OUTPUT_TOKENS` cap in `TopicDedupFilter`) can starve the answer and return truncated/empty content. If a stage benefits from reasoning, **raise that stage's `maxTokens`** accordingly.
2. Latency.

Keep reasoning off for deterministic structured extraction (article scoring, topic dedup, recap) where it only adds latency and cost; reserve it for open-ended, multi-step composition if a podcast is configured for a reasoning model. Reasoning must never pollute parsed `content` — see SA4.

---

## Rule SA3: Prefer Spring AI 2.0 self-correcting structured output over hand-rolled retry loops

Spring AI 2.0 ships purpose-built structured-output reliability. Prefer it over the manual `BeanOutputConverter` + `.responseEntity(...)` + blind `for (attempt in 1..maxRetries)` loops this codebase currently uses (`ArticleScoreSummarizer`, `TopicDedupFilter`).

- **`validateSchema()`** — validates the parsed object against the JSON schema and, on failure, re-issues the prompt **with the specific validation error appended** (default 3 attempts). Provider-agnostic. This is the portable win and the correct replacement for the feedback-less backoff retry loops here (which just re-send the identical prompt).
- **`useProviderStructuredOutput()`** — provider-native schema enforcement (`response_format`/json_schema). **Verify per-model before relying on it:** requests go out over an OpenAI-compatible API to whatever provider/model the podcast selected (OpenRouter → Anthropic/DeepSeek/GLM, or OpenAI, or Ollama), where json_schema passthrough is model-dependent and historically spotty. Treat it as an optimization layered on top of `validateSchema()`, never as the sole guard.
- Recommended shape for high-reliability extraction:
  ```kotlin
  val result = chatClient.prompt()
      .user(prompt)
      .options(OpenAiChatOptions.builder().model(resolvedModel.model).temperature(0.3))
      .call()
      .entity(SomeType::class.java) { spec -> spec.useProviderStructuredOutput().validateSchema() }
  ```
- `.entity(...)`/`.responseEntity(...)` are `.call()`-only (see SA1).
- **Jackson**: this project is on Jackson 3.x (`tools.jackson.*`) and configures Jackson via `spring.jackson.*` in `application.yaml`, not programmatically. When a converter needs a mapper (as `BeanOutputConverter(Type::class.java, jsonMapper)` does today), **inject the Spring-managed `tools.jackson.databind.json.JsonMapper` bean** — never `new` a mapper. `CoveredTopicsExtractor` currently builds its own `jacksonObjectMapper()`; migrate it to the injected bean when touched.
- When extending or fixing these services, migrate toward the native dials (`validateSchema()`, injected `JsonMapper`) rather than adding more manual retry plumbing.

---

## Rule SA4: With reasoning ON, thinking must never pollute parsed content

If a stage is pointed at a reasoning-capable model (SA2):

- Rely on the non-streaming `.call()` path so thinking is resolved server-side and stays out of `content`.
- Keep any defensive extraction (delimited-block extraction like `TopicOrderExtractor`/`CoveredTopicsExtractor`, or a lenient converter) so leading prose/`<think>` text is peeled before parse.
- Back structured parses with `validateSchema()` (SA3) so a residual thinking-leak triggers a self-correcting retry instead of a hard failure.
- Never assume `chatResponse.result.output.text` is pure JSON — extract, then validate.

---

## Rule SA5: Build the ChatClient centrally; keep per-call options consistent

- Build the `ChatClient` via `ChatClientFactory` (`createForModel` for filter/score/recap stages, `createForCompose` when the compose stage needs tools). It resolves the per-user provider config, constructs the `OpenAiChatModel`, and wraps it in `CachingChatModel`. Do not construct `OpenAiChatModel` or an `OpenAIClient` in a service.
- Per-call `OpenAiChatOptions` are built at the call site today (`.options(OpenAiChatOptions.builder().model(resolvedModel.model)...)`). Always set the model from the `ResolvedModel`, and mirror the existing conventions: **temperature `0.3` for structured extraction** (scoring, dedup), the podcast-configured compose temperature via `resolveTemperature(...)` for composition, and a `maxTokens` cap on any stage that can degenerate into a repetition loop (as `TopicDedupFilter` does). Do not invent new ad-hoc option shapes; if option construction starts repeating, factor it out rather than copy-pasting.
- `CachingChatModel` caches on the `USER`+`SYSTEM` prompt hash and must keep delegating `getOptions()` so the provider option type (`OpenAiChatOptions`) survives ChatClient's option merge. It already refuses to cache blank/empty completions — do not weaken that guard.
- OpenAI SDK transport retries (built into the SDK client) handle transient network failures. That is **not** a substitute for `validateSchema()` (SA3), which handles *semantic* correctness.

---

## Rule SA6: Every LLM call records usage and fails loudly enough

- Derive `TokenUsage.fromChatResponse(response)` and cost via `CostEstimator.estimateLlmCostCents(...)` for every call, and persist it onto the article / pipeline stage result the way existing services do (see `ArticleScoreSummarizer` writing `llmInputTokens`/`llmOutputTokens`/`llmCostCents`, and `LlmPipeline` aggregating stage costs). A new call that skips this breaks cost tracking and the cost gate.
- **Pricing lives in `application.yaml`** under `app.models.<provider>.<model>` (`input-cost-per-mtok` / `output-cost-per-mtok`, and `cost-per-million-chars` for TTS), mapped to `ModelCost`. When adding a model a podcast can select, add its pricing there so `ResolvedModel.cost` is non-null and cost estimation works; a missing entry silently yields a null cost.
- Empty/blank content is a failure, not an empty result: throw (as the composers do: `IllegalStateException("Empty response ...")`) or retry via `validateSchema()`; never silently return an empty object as if it were valid data. (Graceful empty-list fallbacks are only acceptable where an empty result is genuinely valid domain output, as in `CoveredTopicsExtractor`'s parse-miss degrade path.)
- Never log prompt/response content that could contain secrets or PII, and never log the resolved API key or base URL.
