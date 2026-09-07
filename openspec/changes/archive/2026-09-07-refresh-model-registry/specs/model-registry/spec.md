## MODIFIED Requirements

### Requirement: Named model definitions in application configuration
The system SHALL support defining models in `application.yaml` under `app.models`, organized as a two-level nested map: `app.models.<provider>.<model-name>`. Each model entry SHALL have a `type` field with value `llm` or `tts`. LLM models MAY have `input-cost-per-mtok` and `output-cost-per-mtok` fields (USD per million tokens). TTS models MAY have a `cost-per-million-chars` field (USD per million characters). Each entry MAY have a `selectable` field (boolean, default `true`). The models SHALL be loaded into `AppProperties` at startup as a `Map<String, Map<String, ModelCost>>` (provider to model name to cost definition).

An entry marked `selectable: false` SHALL remain fully usable for cost resolution while being withheld from the models offered for per-podcast selection. A registry entry is the only means by which a past episode's cost is resolved (`ModelResolver` yields `cost = null` when a model is absent), so an entry SHALL NOT be deleted merely because the model can no longer serve a request. A model SHALL be marked unselectable when every one of its endpoints is rejected by the routing quantization floor, which is the case for every vendor-native model: OpenAI, Anthropic and Google report their endpoints' quantization as `unknown`, so such a model answers any request with `404 No endpoints found` and offering it for selection can only produce a generation that cannot succeed.

The `app.models.inworld` registry SHALL include the following TTS entries: `inworld-tts-1.5-max`, `inworld-tts-1.5-mini`, and `inworld-tts-2`.

The `app.models.openrouter` registry SHALL include the Anthropic Claude 5 generation as LLM entries: `anthropic/claude-sonnet-5` (input 2.00, output 10.00 USD per Mtok) and `anthropic/claude-opus-5` (input 5.00, output 25.00 USD per Mtok), alongside the Claude 4.x entries. All of these SHALL be marked `selectable: false`, as SHALL `openai/gpt-5.4-nano`.

Pricing values for every registry entry SHALL be taken from the provider's published rates rather than estimated, and for an OpenRouter model SHALL be the cheapest endpoint that clears the routing quantization floor, since that is the price routing can actually select. The model-level rate quoted in a catalogue listing is not sufficient on its own, because it may belong to an endpoint the floor excludes.

A registry key MAY be a provider routing alias rather than a pinned model release, in which case the key SHALL be the exact slug the provider's API accepts (including any prefix such as OpenRouter's leading `~`) and its configured pricing SHALL be treated as approximate, since the alias resolves to whichever release is current. The `app.models.openrouter` registry SHALL include the alias `~deepseek/deepseek-v4-flash-latest` (input 0.05, output 0.16 USD per Mtok) alongside the pinned `deepseek/deepseek-v4-flash` entry. Because an alias's target moves without notice, and a new target can carry different reasoning defaults, a pinned release SHALL be preferred for a stage default.

#### Scenario: LLM models defined under provider
- **WHEN** `application.yaml` contains `app.models.openrouter` with entries `openai/gpt-5.4-nano` (type: llm, input-cost-per-mtok: 0.20) and `anthropic/claude-sonnet-4.6` (type: llm, input-cost-per-mtok: 3.00)
- **THEN** `AppProperties.models["openrouter"]` contains two `ModelCost` entries keyed by `openai/gpt-5.4-nano` and `anthropic/claude-sonnet-4.6`, both with type LLM

#### Scenario: Claude 5 models registered under OpenRouter
- **WHEN** the application starts with `app.models.openrouter` containing `anthropic/claude-sonnet-5` (type: llm, input-cost-per-mtok: 2.00, output-cost-per-mtok: 10.00) and `anthropic/claude-opus-5` (type: llm, input-cost-per-mtok: 5.00, output-cost-per-mtok: 25.00), both `selectable: false`
- **THEN** `AppProperties.models["openrouter"]` contains both entries with type LLM and those per-Mtok costs, alongside the existing Claude 4.x entries
- **AND** `GET /config/defaults` does NOT list them under `availableModels.openrouter`, because every Anthropic endpoint is rejected by the routing quantization floor, while their pricing still resolves for any episode generated on them

#### Scenario: An unselectable model still resolves a cost
- **WHEN** a podcast overrides a stage to `anthropic/claude-opus-5`, which is marked `selectable: false`
- **THEN** `ModelResolver` resolves it to a `ResolvedModel` carrying that entry's pricing rather than a null cost

#### Scenario: Selectable defaults to true
- **WHEN** a registry entry omits `selectable`
- **THEN** it is treated as selectable and appears among the models offered for selection

#### Scenario: Routing alias registered under OpenRouter
- **WHEN** the application starts with `app.models.openrouter` containing `~deepseek/deepseek-v4-flash-latest` (type: llm, input-cost-per-mtok: 0.05, output-cost-per-mtok: 0.16)
- **THEN** `AppProperties.models["openrouter"]` contains an entry keyed by the full alias slug `~deepseek/deepseek-v4-flash-latest`, with the leading `~` preserved so the slug can be sent to the OpenRouter API unchanged
- **AND** the pinned `deepseek/deepseek-v4-flash` entry remains present as a separate entry with its own pricing

#### Scenario: TTS models defined under provider
- **WHEN** `application.yaml` contains `app.models.inworld` with entry `inworld-tts-1.5-max` (type: tts, cost-per-million-chars: 10.00)
- **THEN** `AppProperties.models["inworld"]` contains a `ModelCost` entry keyed by `inworld-tts-1.5-max` with type TTS

#### Scenario: Inworld TTS-2 model registered
- **WHEN** `application.yaml` contains `app.models.inworld` with entry `inworld-tts-2` (type: tts, cost-per-million-chars: 35.00)
- **THEN** `AppProperties.models["inworld"]` contains a `ModelCost` entry keyed by `inworld-tts-2` with type TTS and cost-per-million-chars 35.00, alongside the existing `inworld-tts-1.5-max` and `inworld-tts-1.5-mini` entries

#### Scenario: Multiple providers defined
- **WHEN** `application.yaml` contains models under `openrouter`, `inworld`, and `openai` providers
- **THEN** `AppProperties.models` contains three provider keys, each with their respective model entries

#### Scenario: No models defined
- **WHEN** `application.yaml` does not define any `app.models` entries
- **THEN** `AppProperties.models` is an empty map

### Requirement: Global stage-to-model defaults
The system SHALL support mapping pipeline stages to model defaults in `application.yaml` under `app.llm.defaults`. The supported stage names SHALL be `filter`, `dedup` and `compose`. Each default SHALL be an object with `provider` (string) and `model` (string) fields, referencing a model defined under `app.models.<provider>.<model>`. The defaults in `application.yaml` and the code-level `StageDefaults` fallbacks SHALL agree.

Every stage default SHALL be a model that clears the routing quantization floor, since a default that cannot route makes every generation for a podcast without an override fail with `404 No endpoints found`. A vendor-native model therefore SHALL NOT be a stage default.

#### Scenario: Default stage mappings configured
- **WHEN** `application.yaml` contains `app.llm.defaults.filter` with `provider: openrouter, model: deepseek/deepseek-v4-flash-0731` and `app.llm.defaults.compose` with `provider: openrouter, model: z-ai/glm-5.3`
- **THEN** the filter stage resolves to the `openrouter`/`deepseek/deepseek-v4-flash-0731` model cost and the compose stage resolves to the `openrouter`/`z-ai/glm-5.3` model cost

#### Scenario: A stage default clears the routing floor
- **WHEN** the stage defaults are read at startup
- **THEN** none of them names a model whose every endpoint the quantization floor rejects

#### Scenario: Default references non-existent model
- **WHEN** `application.yaml` contains `app.llm.defaults.filter` with `provider: openrouter, model: nonexistent` and no such model exists under `app.models.openrouter`
- **THEN** the system SHALL throw an error at pipeline runtime when the filter stage is invoked
