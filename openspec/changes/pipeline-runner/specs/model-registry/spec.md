## MODIFIED Requirements

### Requirement: Model resolution for a pipeline stage
The system SHALL resolve the model for a given pipeline stage using the following resolution chain, highest precedence first: (1) an override on the current run's configuration for that stage, (2) the podcast's `llm_models` JSON for a stage-specific override containing `{provider, model}`, (3) the global stage default from `app.llm.defaults`. The resulting `{provider, model}` pair SHALL be looked up in `app.models[provider][model]`. If the model is not found, the system SHALL return a `ResolvedModel` with `cost = null` (rather than throwing an exception).

#### Scenario: Run override takes precedence over the podcast's own setting
- **WHEN** a run's configuration overrides the compose stage to `{provider: openrouter, model: z-ai/glm-5.3}` and the podcast's own `llm_models` names a different compose model
- **THEN** that run's compose stage uses `openrouter`/`z-ai/glm-5.3`, and the podcast's own stored setting is unchanged

#### Scenario: Podcast override takes precedence
- **WHEN** a podcast has `llm_models` set to `{"compose": {"provider": "openrouter", "model": "anthropic/claude-opus-4.7"}}`, the global default for compose is a different model, and the run carries no compose override
- **THEN** the compose stage uses the `openrouter`/`anthropic/claude-opus-4.7` model cost

#### Scenario: Global default used when no podcast override
- **WHEN** a podcast has `llm_models` set to `null`, the run carries no override, and the global default for filter is `{provider: openrouter, model: openai/gpt-5.4-nano}`
- **THEN** the filter stage uses the `openrouter`/`openai/gpt-5.4-nano` model cost

#### Scenario: Podcast override for one stage, default for another
- **WHEN** a podcast has `llm_models` set to `{"compose": {"provider": "openrouter", "model": "anthropic/claude-opus-4.7"}}` (no filter override), the run carries no overrides, and the global default for filter is `{provider: openrouter, model: openai/gpt-5.4-nano}`
- **THEN** the filter stage uses `openai/gpt-5.4-nano` and the compose stage uses `anthropic/claude-opus-4.7`

#### Scenario: Unknown model name returns null cost
- **WHEN** a podcast has `llm_models` set to `{"filter": {"provider": "openrouter", "model": "nonexistent"}}` and no such model exists under `app.models.openrouter`
- **THEN** the system returns a `ResolvedModel` with `cost = null` (the pipeline proceeds without cost tracking for that stage)
