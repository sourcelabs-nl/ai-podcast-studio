## ADDED Requirements

### Requirement: OpenRouter requests carry a provider quality floor
Every request to a model resolved to the `openrouter` provider SHALL carry OpenRouter's provider-routing block, restricting `quantizations` to `fp8`, `fp16`, `bf16` and `fp32`, and setting `require_parameters` to true. `allow_fallbacks` SHALL be left at its default so that one endpoint being unavailable still falls through to another.

The block SHALL be sent through `OpenAiChatOptions.extraBody`, and SHALL be omitted for any other provider, since the field is meaningless to the direct `openai` provider.

Without a floor, OpenRouter picks from every endpoint serving the model. For `z-ai/glm-5.3` that is 25 endpoints whose 30-minute uptime ranges from 87.6% to 100% and whose quantization ranges from fp4 to bf16, nine of them fp4 or unknown. A lossy endpoint follows instructions worse, which is how a compose response arrived with no speaker tags at all and another with a missing `finish_reason`.

The floor SHALL keep enough endpoints to stay redundant: at the time of writing it leaves 9 for the compose model and 11 for the filter and dedup model, and every one of those supports the parameters the pipeline actually sends.

#### Scenario: An OpenRouter request carries the floor
- **WHEN** a stage issues a request for a model whose provider is `openrouter`
- **THEN** the request body contains a `provider` object with the four accepted quantizations and `require_parameters` true

#### Scenario: A non-OpenRouter request does not
- **WHEN** a stage issues a request for a model resolved to the direct `openai` provider
- **THEN** no `provider` routing block is sent

#### Scenario: Fallbacks stay available
- **WHEN** the floor is applied
- **THEN** `allow_fallbacks` is not disabled, so an unavailable endpoint falls through to another that meets the floor

### Requirement: Reasoning is decided per stage, not by the provider
Each stage SHALL state its reasoning intent explicitly rather than leaving it to the routed provider. Composition SHALL request reasoning, at an effort configured as `app.compose.reasoning-effort` (default `medium`). Article scoring, topic dedup and recap generation SHALL request `none`.

OpenRouter treats an omitted `reasoning` parameter as "inferred from model defaults" and counts reasoning tokens as output tokens, charged accordingly. Leaving it unset therefore let the provider decide both the quality and the bill: across five episodes on one model, compose output ranged from 6,048 to 72,821 tokens for scripts of comparable length, with duration between 1 minute and 18 minutes and cost between 4 and 38 cents.

Reasoning belongs to composition, which plans a two-thousand-word script. The other three stages produce a JSON object or a short paragraph, where reasoning tokens are cost without benefit — and where, for dedup, they previously consumed the whole output budget and returned empty content.

The effort SHALL be configurable rather than hard-coded, because it is the largest single cost lever in the pipeline.

#### Scenario: Composition requests reasoning
- **WHEN** a compose request is built and `app.compose.reasoning-effort` is `medium`
- **THEN** the request carries that reasoning effort

#### Scenario: The effort is configurable
- **WHEN** `app.compose.reasoning-effort` is set to `low`
- **THEN** compose requests carry `low` without a code change

#### Scenario: Structured stages disable reasoning
- **WHEN** an article-scoring, topic-dedup or recap request is built
- **THEN** it carries a reasoning effort of `none`

### Requirement: The compose stage retries a transient provider fault
The composition call SHALL be wrapped in a retry that fires only on transient provider faults, namely an invalid or incomplete completion from the provider (`OpenAIInvalidDataException`) and an I/O failure (`ResourceAccessException`).

The retry SHALL NOT fire on a speaker-tag validation failure. `RoleTagValidationAdvisor` already re-issues the request up to twice within a single call, so retrying the whole call on that outcome would multiply attempts and cost for a fault that is already handled.

Compose was the only LLM stage without a retry while scoring, dedup and TTS all had one, and it is the most expensive call in the pipeline. A single `finish_reason is null` therefore discarded ten minutes of generation and failed the episode outright.

#### Scenario: An invalid completion is retried
- **WHEN** the provider returns a completion whose `finish_reason` is absent and the client raises `OpenAIInvalidDataException`
- **THEN** the compose call is retried rather than failing the episode

#### Scenario: A tag-validation failure is not retried
- **WHEN** composition fails because no attempt produced a valid speaker tag
- **THEN** the compose retry does not fire and the failure surfaces, because the advisor has already exhausted its own attempts
