## MODIFIED Requirements

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
