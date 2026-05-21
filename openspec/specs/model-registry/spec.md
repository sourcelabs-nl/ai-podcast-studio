# Capability: Model Registry

## Purpose

Named model definitions in application configuration with provider and model ID, plus global stage-to-model default mappings for the LLM pipeline.
## Requirements
### Requirement: Named model definitions in application configuration
The system SHALL support defining models in `application.yaml` under `app.models`, organized as a two-level nested map: `app.models.<provider>.<model-name>`. Each model entry SHALL have a `type` field with value `llm` or `tts`. LLM models MAY have `input-cost-per-mtok` and `output-cost-per-mtok` fields (USD per million tokens). TTS models MAY have a `cost-per-million-chars` field (USD per million characters). The models SHALL be loaded into `AppProperties` at startup as a `Map<String, Map<String, ModelCost>>` (provider to model name to cost definition).

The `app.models.inworld` registry SHALL include the following TTS entries: `inworld-tts-1.5-max`, `inworld-tts-1.5-mini`, and `inworld-tts-2`.

#### Scenario: LLM models defined under provider
- **WHEN** `application.yaml` contains `app.models.openrouter` with entries `openai/gpt-5.4-nano` (type: llm, input-cost-per-mtok: 0.20) and `anthropic/claude-sonnet-4.6` (type: llm, input-cost-per-mtok: 3.00)
- **THEN** `AppProperties.models["openrouter"]` contains two `ModelCost` entries keyed by `openai/gpt-5.4-nano` and `anthropic/claude-sonnet-4.6`, both with type LLM

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
The system SHALL support mapping pipeline stages to model defaults in `application.yaml` under `app.llm.defaults`. The supported stage names SHALL be `filter` and `compose`. Each default SHALL be an object with `provider` (string) and `model` (string) fields, referencing a model defined under `app.models.<provider>.<model>`.

#### Scenario: Default stage mappings configured
- **WHEN** `application.yaml` contains `app.llm.defaults.filter` with `provider: openrouter, model: openai/gpt-5.4-nano` and `app.llm.defaults.compose` with `provider: openrouter, model: anthropic/claude-sonnet-4.6`
- **THEN** the filter stage resolves to the `openrouter`/`openai/gpt-5.4-nano` model cost and the compose stage resolves to the `openrouter`/`anthropic/claude-sonnet-4.6` model cost

#### Scenario: Default references non-existent model
- **WHEN** `application.yaml` contains `app.llm.defaults.filter` with `provider: openrouter, model: nonexistent` and no such model exists under `app.models.openrouter`
- **THEN** the system SHALL throw an error at pipeline runtime when the filter stage is invoked

### Requirement: Model resolution for a pipeline stage
The system SHALL resolve the model for a given pipeline stage using the following resolution chain: (1) check the podcast's `llm_models` JSON for a stage-specific override containing `{provider, model}`, (2) fall back to the global stage default from `app.llm.defaults`. The resulting `{provider, model}` pair SHALL be looked up in `app.models[provider][model]`. If the model is not found, the system SHALL return a `ResolvedModel` with `cost = null` (rather than throwing an exception).

#### Scenario: Podcast override takes precedence
- **WHEN** a podcast has `llm_models` set to `{"compose": {"provider": "openrouter", "model": "anthropic/claude-opus-4.7"}}` and the global default for compose is a different model
- **THEN** the compose stage uses the `openrouter`/`anthropic/claude-opus-4.7` model cost

#### Scenario: Global default used when no podcast override
- **WHEN** a podcast has `llm_models` set to `null` and the global default for filter is `{provider: openrouter, model: openai/gpt-5.4-nano}`
- **THEN** the filter stage uses the `openrouter`/`openai/gpt-5.4-nano` model cost

#### Scenario: Podcast override for one stage, default for another
- **WHEN** a podcast has `llm_models` set to `{"compose": {"provider": "openrouter", "model": "anthropic/claude-opus-4.7"}}` (no filter override) and the global default for filter is `{provider: openrouter, model: openai/gpt-5.4-nano}`
- **THEN** the filter stage uses `openai/gpt-5.4-nano` and the compose stage uses `anthropic/claude-opus-4.7`

#### Scenario: Unknown model name returns null cost
- **WHEN** a podcast has `llm_models` set to `{"filter": {"provider": "openrouter", "model": "nonexistent"}}` and no such model exists under `app.models.openrouter`
- **THEN** the system returns a `ResolvedModel` with `cost = null` (the pipeline proceeds without cost tracking for that stage)

### Requirement: Tavily research provider entry

The model registry SHALL include a `tavily` provider entry under `app.research.tavily` exposing a per-call price (`cost-per-call-cents`) used to compute `research_cost_cents` per episode. The provider entry SHALL be loaded into `AppProperties` and surfaced through `GET /config/defaults` so the frontend can determine whether Tavily research is available.

#### Scenario: Provider entry loads

- **WHEN** the application starts with `app.research.tavily.cost-per-call-cents=0.4` configured
- **THEN** `AppProperties.research.tavily.costPerCallCents` is `0.4`

#### Scenario: Defaults endpoint exposes research provider

- **WHEN** the frontend calls `GET /config/defaults`
- **THEN** the response includes a `research` section listing `tavily` with its per-call cost

