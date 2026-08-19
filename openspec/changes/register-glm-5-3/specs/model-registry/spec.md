## MODIFIED Requirements

### Requirement: Named model definitions in application configuration
The system SHALL support defining models in `application.yaml` under `app.models`, organized as a two-level nested map: `app.models.<provider>.<model-name>`. Each model entry SHALL have a `type` field with value `llm` or `tts`. LLM models MAY have `input-cost-per-mtok` and `output-cost-per-mtok` fields (USD per million tokens). TTS models MAY have a `cost-per-million-chars` field (USD per million characters). The models SHALL be loaded into `AppProperties` at startup as a `Map<String, Map<String, ModelCost>>` (provider to model name to cost definition).

The `app.models.inworld` registry SHALL include the following TTS entries: `inworld-tts-1.5-max`, `inworld-tts-1.5-mini`, and `inworld-tts-2`.

The `app.models.openrouter` registry SHALL include the Anthropic Claude 5 generation as LLM entries: `anthropic/claude-sonnet-5` (input 2.00, output 10.00 USD per Mtok) and `anthropic/claude-opus-5` (input 5.00, output 25.00 USD per Mtok), alongside the Claude 4.x entries. Pricing values for every registry entry SHALL be taken from the provider's published pricing (for OpenRouter models, the OpenRouter model catalog) rather than estimated.

The `app.models.openrouter` registry SHALL include the Z.ai GLM entries `z-ai/glm-5.2` (input 1.00, output 4.00 USD per Mtok) and `z-ai/glm-5.3` (input 1.40, output 4.40 USD per Mtok). Where a model is served by several providers, the configured price SHALL reflect the cheapest provider OpenRouter routing selects, as it does for GLM-5.2; where a single provider serves the model, as with GLM-5.3, its list price is the routed price.

A registry key MAY be a provider routing alias rather than a pinned model release, in which case the key SHALL be the exact slug the provider's API accepts (including any prefix such as OpenRouter's leading `~`) and its configured pricing SHALL be treated as approximate, since the alias resolves to whichever release is current. The `app.models.openrouter` registry SHALL include the alias `~deepseek/deepseek-v4-flash-latest` (input 0.08, output 0.252 USD per Mtok) alongside the pinned `deepseek/deepseek-v4-flash` entry.

#### Scenario: LLM models defined under provider
- **WHEN** `application.yaml` contains `app.models.openrouter` with entries `openai/gpt-5.4-nano` (type: llm, input-cost-per-mtok: 0.20) and `anthropic/claude-sonnet-4.6` (type: llm, input-cost-per-mtok: 3.00)
- **THEN** `AppProperties.models["openrouter"]` contains two `ModelCost` entries keyed by `openai/gpt-5.4-nano` and `anthropic/claude-sonnet-4.6`, both with type LLM

#### Scenario: Claude 5 models registered under OpenRouter
- **WHEN** the application starts with `app.models.openrouter` containing `anthropic/claude-sonnet-5` (type: llm, input-cost-per-mtok: 2.00, output-cost-per-mtok: 10.00) and `anthropic/claude-opus-5` (type: llm, input-cost-per-mtok: 5.00, output-cost-per-mtok: 25.00)
- **THEN** `AppProperties.models["openrouter"]` contains both entries with type LLM and those per-Mtok costs, alongside the existing Claude 4.x entries
- **AND** `GET /config/defaults` lists `anthropic/claude-sonnet-5` and `anthropic/claude-opus-5` under `availableModels.openrouter` so they can be selected as per-podcast stage overrides

#### Scenario: GLM-5.3 registered under OpenRouter
- **WHEN** the application starts with `app.models.openrouter` containing `z-ai/glm-5.3` (type: llm, input-cost-per-mtok: 1.40, output-cost-per-mtok: 4.40)
- **THEN** `AppProperties.models["openrouter"]` contains the entry with type LLM and those per-Mtok costs, alongside the existing `z-ai/glm-5.2` entry
- **AND** `GET /config/defaults` lists `z-ai/glm-5.3` under `availableModels.openrouter` so it can be selected as a per-podcast stage override

#### Scenario: Routing alias registered under OpenRouter
- **WHEN** the application starts with `app.models.openrouter` containing `~deepseek/deepseek-v4-flash-latest` (type: llm, input-cost-per-mtok: 0.08, output-cost-per-mtok: 0.252)
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
