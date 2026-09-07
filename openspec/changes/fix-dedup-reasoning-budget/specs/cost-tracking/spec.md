## ADDED Requirements

### Requirement: Every stage states its reasoning effort, including none
Each stage SHALL state its reasoning intent explicitly rather than leaving it to the routed provider, in the form the resolved provider actually reads.

For a model resolved to `openrouter`, the effort SHALL be sent in the request's extra body as a `reasoning` object carrying `effort`, alongside the `provider` block. This SHALL include an effort of `none`, sent as an explicit `effort: "none"`. The flat `reasoning_effort` field SHALL NOT be sent to OpenRouter: its documentation states that reasoning is controlled by the `reasoning` object and that `reasoning_effort` is not accepted as an alias, and combined with `require_parameters` an unsupported parameter could steer routing on a field the provider never reads. For a model resolved to the direct `openai` provider, the flat `reasoning_effort` field SHALL be used, where it is the correct one.

Omitting the block SHALL NOT be used to express "no reasoning". OpenRouter infers an absent reasoning parameter from the model's own default, and the dedup and filter model `~deepseek/deepseek-v4-flash-latest` reports `default_enabled: true` at `default_effort: "high"`. Measured on the live API for that model with a one-line task, an omitted block cost 47 reasoning tokens, `effort: "low"` cost 22, and `effort: "none"` cost none. Since reasoning tokens are charged as output and drawn from the same `max_tokens` a stage sets for its own output, omitting the block consumed the entire dedup budget and returned empty content, failing episode 200 five times over.

Composition SHALL request reasoning at an effort configured as `app.compose.reasoning-effort` (default `medium`), overridable per podcast via `composeSettings.reasoningEffort`. Article scoring, topic dedup and recap generation SHALL request `none`: they produce a JSON object or a short paragraph, where reasoning is cost without benefit.

An effort the resolved endpoint cannot honour SHALL be allowed to fail rather than be silently softened. An endpoint reporting `mandatory: true` rejects `effort: "none"` outright, as `z-ai/glm-5.3` does with *"Reasoning is mandatory for this endpoint and cannot be disabled"* (HTTP 400). Configuring such an effort is a configuration error, and a 400 naming the cause is preferable to sending nothing and receiving the provider's default instead.

The `reasoning` object SHALL set `exclude` so the reasoning text is kept out of the response. The tokens are billed either way, and the client cannot read it regardless: reasoning comes back in `message.reasoning`, for which `openai-java`'s `ChatCompletionMessage` declares no field. Excluding it also means it cannot be mistaken for the script.

OpenRouter treats an omitted reasoning parameter as inferred from model defaults and counts reasoning tokens as output tokens, charged accordingly. Leaving it unset let the provider decide both the quality and the bill: across five episodes on one model, compose output ranged from 6,048 to 72,821 tokens for scripts of comparable length, with duration between 1 minute and 18 minutes and cost between 4 and 38 cents. Sending it in a form the provider does not read has the same effect, which is what setting only the flat field did.

The effort SHALL be configurable rather than hard-coded, because it is the largest single cost lever in the pipeline.

#### Scenario: Composition requests reasoning
- **WHEN** a compose request is built for an `openrouter` model and `app.compose.reasoning-effort` is `medium`
- **THEN** the extra body's `reasoning` object carries `effort` `medium`, and no flat `reasoning_effort` field is sent

#### Scenario: The effort is configurable
- **WHEN** `app.compose.reasoning-effort` is set to `low`
- **THEN** compose requests carry `low` without a code change

#### Scenario: A podcast overrides the effort
- **WHEN** a podcast's `composeSettings.reasoningEffort` is `low`
- **THEN** its compose requests carry `low` in the `reasoning` block

#### Scenario: A direct OpenAI model uses the flat field
- **WHEN** a compose request is built for a model resolved to the direct `openai` provider
- **THEN** it carries `reasoning_effort` and no extra body

#### Scenario: An effort of none is stated explicitly
- **WHEN** a request is built for an `openrouter` model with an effort of `none`
- **THEN** the extra body carries the `provider` block and a `reasoning` object with `effort` `none`

#### Scenario: The structured stages suppress reasoning
- **WHEN** an article-scoring, topic-dedup or recap request is built for an `openrouter` model
- **THEN** it carries a `reasoning` object with `effort` `none`, rather than no block

#### Scenario: A mandatory-reasoning endpoint rejects none
- **WHEN** an effort of `none` is configured for a model whose endpoint reports reasoning as mandatory
- **THEN** the request fails with the provider's 400 rather than falling back to the provider's default effort

#### Scenario: Reasoning text is excluded from the response
- **WHEN** a `reasoning` block is sent
- **THEN** it sets `exclude`, so the provider returns the reasoning tokens' effect but not their text

## REMOVED Requirements

### Requirement: Reasoning is decided per stage, not by the provider
**Reason**: Its central rule inverted. The requirement mandated that an effort of `none` "SHALL send no `reasoning` block at all rather than an explicit `effort: \"none\"`", reasoning that `require_parameters` made an unoffered reasoning parameter a routing hazard, and that article scoring, topic dedup and recap "take their non-reasoning behaviour from the model they run on". Both premises proved false against the live API: an explicit `effort: "none"` routes successfully with `require_parameters: true`, and the model those stages run on reasons at high effort by default, so sending nothing bought exactly the reasoning the requirement intended to avoid.

**Migration**: Replaced by "Every stage states its reasoning effort, including none", which keeps the extra-body form, the `exclude` rule, the per-stage efforts and the configurability, and changes only the handling of `none`. The scenario "An effort of none sends no reasoning block" is replaced by "An effort of none is stated explicitly", and "Structured stages disable reasoning" by "The structured stages suppress reasoning".
