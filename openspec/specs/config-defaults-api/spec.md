## Purpose

Defines the API endpoint for exposing system-level podcast configuration defaults to the frontend.

## Requirements

### Requirement: Config defaults endpoint
The system SHALL provide a `GET /config/defaults` endpoint that returns the system default values for podcast settings derived from application configuration. The response SHALL include: `llmModels` (object with `filter`, `dedup` and `compose` keys, each containing `{provider, model}`), `availableModels` (object with provider keys, each containing a list of `{name, type}` entries), `maxLlmCostCents`, `targetWords`, `fullBodyThreshold`, `maxArticleAgeDays`.

`availableModels` SHALL contain only the models a podcast may actually choose: an entry marked `selectable: false` in the registry SHALL be omitted, because such a model can no longer serve a request and offering it can only produce a generation that fails. Every provider key present in the registry SHALL be retained even when all of its models are withheld, so the settings page's grouping and its choice of active provider do not shift as models come and go.

The filtering SHALL live outside the controller, which validates input, delegates and maps responses.

#### Scenario: Successful response with structured defaults
- **WHEN** a GET request is made to `/config/defaults` and defaults are `filter: {provider: openrouter, model: deepseek/deepseek-v4-flash-0731}`, `compose: {provider: openrouter, model: z-ai/glm-5.3}`
- **THEN** the response `llmModels` SHALL contain `{"filter": {"provider": "openrouter", "model": "deepseek/deepseek-v4-flash-0731"}, "compose": {"provider": "openrouter", "model": "z-ai/glm-5.3"}}`

#### Scenario: Available models grouped by provider with type
- **WHEN** a GET request is made to `/config/defaults` and `app.models` contains openrouter (LLM models) and inworld (TTS models), all selectable
- **THEN** the response `availableModels` SHALL contain `{"openrouter": [{"name": "z-ai/glm-5.3", "type": "llm"}, ...], "inworld": [{"name": "inworld-tts-1.5-max", "type": "tts"}, ...]}`

#### Scenario: An unselectable model is withheld from the picker
- **WHEN** `app.models.openrouter` contains `z-ai/glm-5.3` and `anthropic/claude-opus-5`, the latter marked `selectable: false`
- **THEN** `availableModels.openrouter` lists only `z-ai/glm-5.3`

#### Scenario: A provider whose every model is withheld keeps its key
- **WHEN** every model under a provider is marked `selectable: false`
- **THEN** that provider key is still present in `availableModels`, with an empty list
