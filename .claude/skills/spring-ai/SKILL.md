---
name: spring-ai
description: Spring AI rules for this project's LLM features (article scoring, topic dedup, script composition, recap generation, streaming vs .call(), structured extraction, per-stage model config, cost tracking). Consult BEFORE creating or changing any AI/LLM feature (ArticleScoreSummarizer, TopicDedupFilter, BriefingComposer, DialogueComposer, InterviewComposer, EpisodeRecapGenerator, ChatClientFactory, ModelResolver, CachingChatModel) and during review of files under com.aisummarypodcast.llm or anything using ChatModel/ChatClient/OpenAiChatOptions/BeanOutputConverter.
user-invocable: false
---

# Spring AI Rules

Rules for building and reviewing this project's LLM features. **Consult this skill before touching any Spring AI code** (`ChatModel`, `ChatClient`, `OpenAiChatOptions`, `BeanOutputConverter`, structured extraction, prompts, `ChatClientFactory`, `ModelResolver`, `CachingChatModel`, cost/usage tracking). All of it lives under `com.aisummarypodcast.llm`.

## Stack baseline

- **Spring AI 2.x** (Spring Boot 4); the exact version is `spring-ai.version` in `pom.xml`. The chat model is built on the **official OpenAI Java SDK** (`OpenAiChatModel.builder().openAiClient(...)`), so the provider is any **OpenAI-compatible API**: OpenRouter (the default), OpenAI directly, or a local Ollama. The Spring AI OpenAI autoconfiguration is disabled (see `spring.autoconfigure.exclude` in `application.yaml`); models are constructed by hand in `ChatClientFactory`.
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
- When extending or fixing these services, migrate toward the native dials (`validateSchema()`, injected `JsonMapper`) rather than adding more manual retry plumbing. Three constraints decide whether that migration is actually available for a given stage:
  - **Retry nesting is multiplicative.** `StructuredOutputValidationAdvisor` re-issues inside the call and defaults to `maxRepeatAttempts = 3`, which nests inside that stage's Resilience4j instance (`app.resilience.retry.instances.<stage>`, itself 3 attempts on `external-api`). Left at the defaults that is up to 9 model calls, each bounded only by the stage timeout (5m for dedup, 20m for compose). Whenever an advisor re-issues inside the call, lower the outer `max-attempts` to match, the way compose already does at 2 for `RoleTagValidationAdvisor` (`application.yaml`).
  - **Pass the injected `JsonMapper` explicitly.** The advisor's builder defaults to `JacksonUtils.getDefaultJsonMapper()`, not the Spring-managed bean, so taking the default silently violates SB6.
  - **Schema validation cannot express a partial result that is deliberately good enough.** `TopicDedupFilter` accepts a truncated response once it still selects `app.compose.max-articles` articles, because the discarded tail provably could not change the episode. Validation would reject it and buy a full extra dedup call (16s to 1m44s) for nothing. A stage with a salvage rule of that kind keeps its hand-rolled parse; a stage without one does not.
- **A retry must never re-send the byte-identical prompt.** `CachingChatModel` keys on `USER`+`SYSTEM` prompt text and refuses only blank completions, so an unparseable-but-non-blank answer is cached and every identical retry replays it in milliseconds without reaching the model. This has cost two incidents: nine permanently unscorable articles, and episode 202 burning three dedup attempts at four milliseconds each. Where the native `validateSchema()` is used this is handled for you, since the advisor appends the validation error to the `UserMessage` and so changes the key. Where a stage still owns its retry loop, escalate the prompt per attempt as `ArticleScoreSummarizer.promptForAttempt` and `TopicDedupFilter.promptForAttempt` do: unchanged on attempt 1, then a correction naming the attempt number and restating the wanted shape.

---

## Rule SA4: Never assume a parsed response is pure JSON

A model answers off-schema whether or not it reasons. Reasoning is one source of pollution; plain instruction drift is another, and turning reasoning off does not buy you a clean parse. Episode 202's dedup call ran with an explicit `effort: "none"`, measured at 0 reasoning tokens, and still returned complete, valid JSON behind a `**Output:**` lead-in, inside a ```json fence, as a bare array instead of the asked-for object.

- Never assume `chatResponse.result.output.text` is pure JSON. Extract, then validate.
- Rely on the non-streaming `.call()` path so any thinking is resolved server-side and stays out of `content` (SA2, SA1).
- Keep defensive extraction on every parsed stage, not only reasoning-capable ones: delimited-block extraction as in `TopicOrderExtractor`/`CoveredTopicsExtractor`, or a lenient parse as in `TopicDedupFilter.parseEitherShape`, which reads a single JSON value starting at the first brace or bracket so a lead-in, a closing fence and trailing chatter are all ignored. Locate only the start of the payload, never its end: scanning for the last closer lets a stray bracket in a sign-off truncate the parse, and on a genuinely truncated response the last closer sits inside the element that was cut off.
- Tolerate the shapes a model actually returns (an object or the bare array inside it) where tolerating them is free. A shape the parser can accept costs nothing; a shape it rejects costs a full retry.
- Back structured parses with `validateSchema()` (SA3) where that rule's constraints allow it, so a residual leak triggers a self-correcting retry instead of a hard failure.

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

---

## Reference docs

Read `spring-ai.version` from `pom.xml` before trusting a remembered API. Spring AI
renamed and moved a great deal between 1.x and 2.0, so recall from training data is
unreliable here in a way it is not for Kotlin or Spring Boot: look it up.

- [Chat Client API](https://docs.spring.io/spring-ai/reference/api/chatclient.html): fluent API for talking to chat models
- [Chat Models](https://docs.spring.io/spring-ai/reference/api/chatmodel.html): model providers and configuration
- [Prompts](https://docs.spring.io/spring-ai/reference/api/prompt.html): prompt creation and templating
- [Structured Output](https://docs.spring.io/spring-ai/reference/api/structured-output-converter.html): converting responses to typed objects (`BeanOutputConverter`)
- [Tool Calling](https://docs.spring.io/spring-ai/reference/api/tools.html): function calling, used by the compose stage
- [Chat Memory](https://docs.spring.io/spring-ai/reference/api/chat-memory.html): conversation history
- [Advisors](https://docs.spring.io/spring-ai/reference/api/advisors.html): intercepting and augmenting calls
- [Audio Models](https://docs.spring.io/spring-ai/reference/api/audio/): speech-to-text and text-to-speech
- [Evaluation Testing](https://docs.spring.io/spring-ai/reference/api/testing.html): evaluating responses for relevancy and factual accuracy
- [Prompt Engineering Patterns](https://docs.spring.io/spring-ai/reference/api/chat/prompt-engineering-patterns.html): prompt design practices
- [Reference home](https://docs.spring.io/spring-ai/reference/): everything else
