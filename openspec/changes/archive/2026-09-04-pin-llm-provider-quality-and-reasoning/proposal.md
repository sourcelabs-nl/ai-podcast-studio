## Why

Episode 197 failed on 4 September and only came through on a third attempt. The pipeline was fine; the compose request was under-specified in two ways, and OpenRouter filled the gaps differently from one day to the next.

**Nothing pinned the provider's quality.** The configuration still claims "Z.ai is the only provider serving GLM-5.3, so its list price is the routed price". OpenRouter now lists **25 endpoints** for that model, from Z.AI to Cloudflare, Together, DeepInfra and twenty others, with 30-minute uptime between 87.6% and 100%, max completion tokens between 128k and 1.18M, and quantization anywhere from **fp4** to bf16. Nine of them are fp4 or unknown. Nothing in the request kept us off them.

**Nothing pinned reasoning.** Only `TopicDedupFilter` sets `reasoningEffort`; compose, scoring and recap send no reasoning parameter at all. OpenRouter's documentation is explicit that for a reasoning-capable model an omitted `reasoning` parameter is "inferred from model defaults", and that reasoning tokens are "considered output tokens and charged accordingly". So the provider decided, and the bill followed:

| episode | compose output tokens | script | duration | cost |
|---|---|---|---|---|
| 190 | 57,546 | 16,192 chars | 18m 11s | 31c |
| 191 | 6,075 | 12,932 chars | 1m 03s | 4c |
| 194 | 29,859 | 14,687 chars | 5m 56s | 15c |
| 195 | 6,048 | 14,596 chars | 1m 10s | 4c |
| 196 | 72,821 | 13,982 chars | 13m 31s | 38c |

Same model, same prompt shape, comparable scripts — and a twelvefold swing in tokens, time and money. A 14,000-character script is roughly 3,500 tokens, so episode 196 spent some 69,000 tokens on hidden reasoning.

On 4 September the draw was a bad one. The 15:00 run died on `OpenAIInvalidDataException: finish_reason is null` — the provider returned a completion whose required field was absent — and ten minutes of generation went with it, because **compose is the only LLM stage without a retry** while scoring, dedup and TTS all have one. The manual retry then produced two consecutive scripts with no speaker tags at all before the third attempt worked, which fits reasoning text arriving where the script was expected, though that part is plausible rather than proven. Both symptoms were new: neither had occurred on any previous day.

## What Changes

- A quality floor on every OpenRouter request: `provider.quantizations` restricted to `fp8`, `fp16`, `bf16` and `fp32`, and `require_parameters: true` so an endpoint that cannot honour the request is skipped. `allow_fallbacks` stays at its default, so a single endpoint going down still falls through to another. Compose keeps 9 eligible endpoints and the filter/dedup model keeps 11, every one of which supports the parameters actually sent. Passed through Spring AI's `OpenAiChatOptions.extraBody`, and only for models resolved to the `openrouter` provider, since the field is meaningless to the direct `openai` provider.
- Reasoning becomes explicit per stage instead of provider-dependent: on for compose, where it plans a two-thousand-word script, and `none` for scoring, dedup and recap, whose jobs are a JSON object or a short paragraph. The compose effort is configurable as `app.compose.reasoning-effort` (default `medium`) and overridable per podcast via `composeSettings.reasoningEffort`, rather than hard-coded, because it is the single largest cost lever in the pipeline and a short digest does not need the planning budget of a long briefing.
- A `compose` retry, restricted to transient provider faults (`OpenAIInvalidDataException` and `ResourceAccessException`). It deliberately does **not** retry a tag-validation failure: `RoleTagValidationAdvisor` already re-issues the request up to twice inside the call, and retrying the whole call on that would multiply attempts and cost.
- The stale single-provider comment is corrected to describe what the routing floor now guarantees.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `cost-tracking`: gains the OpenRouter routing floor and the per-stage reasoning policy as bounds on every LLM call, and a retry on the compose stage.
- `article-dedup-filter`: its existing reasoning setting is restated as part of the per-stage policy.
- `podcast-customization`: `composeSettings` gains a recognised `reasoningEffort` key, validated against the documented efforts.

## Impact

- Backend: new `OpenRouterRouting`; `ComposerUtils.buildComposeOptions` (takes the resolved model, adds reasoning and the routing floor); the three composers; `ArticleScoreSummarizer`, `TopicDedupFilter`, `EpisodeRecapGenerator`; `LlmPipeline.compose` (retry); `ComposeProperties` (new `reasoningEffort`).
- Config: `app.compose.reasoning-effort`, a `compose` retry instance, corrected glm-5.3 comment.
- API: `PodcastController.validateComposeSettings` rejects an unknown `reasoningEffort` with 422.
- Measurement is already in place: `compose_output_tokens` against script length is what exposed the reasoning swing, and it is persisted per episode, so the effect of the floor and of the effort setting can be read off later without new instrumentation.
- Frontend: the Composer Settings key/value editor documents the new key in its description; no new control and no schema change.
