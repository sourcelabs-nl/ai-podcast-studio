## MODIFIED Requirements

### Requirement: Global stage-to-model defaults
The system SHALL support mapping pipeline stages to model defaults in `application.yaml` under `app.llm.defaults`. The supported stage names SHALL be `filter`, `dedup` and `compose`. Each default SHALL be an object with `provider` (string) and `model` (string) fields, referencing a model defined under `app.models.<provider>.<model>`. The defaults in `application.yaml` and the code-level `StageDefaults` fallbacks SHALL agree.

Every stage default SHALL be a model that clears the routing quantization floor, since a default that cannot route makes every generation for a podcast without an override fail with `404 No endpoints found`. A vendor-native model therefore SHALL NOT be a stage default.

A stage default SHALL name a pinned model release rather than a routing alias, so that the reasoning defaults a stage was verified against cannot change without notice.

All three stages SHALL default to `openrouter` / `deepseek/deepseek-v4.1-flash`. It is the strongest model available to each of them that clears the floor, and the prose it writes is what the compose stage is judged on, where the reasoning and coding indices that previously selected `z-ai/glm-5.3` are not. Its registry entry SHALL be priced at input 0.30, output 1.20 USD per Mtok: the vendor's own endpoint is cheaper but reports its quantization as `unknown`, so the floor rejects it, and the cheapest endpoint routing can actually select is what a default costs.

#### Scenario: Default stage mappings configured
- **WHEN** `application.yaml` contains `app.llm.defaults.filter`, `app.llm.defaults.dedup` and `app.llm.defaults.compose`, each with `provider: openrouter, model: deepseek/deepseek-v4.1-flash`
- **THEN** all three stages resolve to the `openrouter`/`deepseek/deepseek-v4.1-flash` model cost

#### Scenario: A stage default clears the routing floor
- **WHEN** the stage defaults are read at startup
- **THEN** none of them names a model whose every endpoint the quantization floor rejects

#### Scenario: Code fallbacks agree with the configuration
- **WHEN** `StageDefaults` is compared with `app.llm.defaults`
- **THEN** every stage names the same provider and model in both

#### Scenario: Default references non-existent model
- **WHEN** `application.yaml` contains `app.llm.defaults.filter` with `provider: openrouter, model: nonexistent` and no such model exists under `app.models.openrouter`
- **THEN** the system SHALL throw an error at pipeline runtime when the filter stage is invoked
