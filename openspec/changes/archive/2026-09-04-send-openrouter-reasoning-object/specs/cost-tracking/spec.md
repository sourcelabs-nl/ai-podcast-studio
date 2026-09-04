## MODIFIED Requirements

### Requirement: Reasoning is decided per stage, not by the provider
Each stage SHALL state its reasoning intent explicitly rather than leaving it to the routed provider, in the form the resolved provider actually reads.

For a model resolved to `openrouter`, the budget SHALL be sent in the request's extra body as a `reasoning` object carrying `effort`, alongside the `provider` block. The flat `reasoning_effort` field SHALL NOT be sent to OpenRouter: its documentation states that reasoning is controlled by the `reasoning` object and that `reasoning_effort` is not accepted as an alias, and combined with `require_parameters` an unsupported parameter could steer routing on a field the provider never reads. For a model resolved to the direct `openai` provider, the flat `reasoning_effort` field SHALL be used, where it is the correct one.

Composition SHALL request reasoning at an effort configured as `app.compose.reasoning-effort` (default `medium`), overridable per podcast via `composeSettings.reasoningEffort`.

An effort of `none` SHALL send no `reasoning` block at all rather than an explicit `effort: "none"`. `require_parameters` restricts routing to endpoints that support every parameter supplied, so asking a deliberately non-reasoning model to acknowledge a reasoning parameter risks leaving no eligible endpoint. Article scoring, topic dedup and recap generation therefore send no reasoning block, and take their non-reasoning behaviour from the model they run on.

The `reasoning` object SHALL set `exclude` so the reasoning text is kept out of the response. The tokens are billed either way, and the text has nowhere to go: it is returned in `message.reasoning`, for which `openai-java`'s `ChatCompletionMessage` declares no field. Excluding it also means it cannot be mistaken for the script.

OpenRouter treats an omitted reasoning parameter as "inferred from model defaults" and counts reasoning tokens as output tokens, charged accordingly. Leaving it unset let the provider decide both the quality and the bill: across five episodes on one model, compose output ranged from 6,048 to 72,821 tokens for scripts of comparable length, with duration between 1 minute and 18 minutes and cost between 4 and 38 cents. Sending it in a form the provider does not read has the same effect, which is what setting only the flat field did.

Reasoning belongs to composition, which plans a two-thousand-word script. The other three stages produce a JSON object or a short paragraph, where reasoning tokens are cost without benefit — and where, for dedup, they previously consumed the whole output budget and returned empty content.

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

#### Scenario: An effort of none sends no reasoning block
- **WHEN** a request is built for an `openrouter` model with an effort of `none`
- **THEN** the extra body carries the `provider` block and no `reasoning` block

#### Scenario: Structured stages disable reasoning
- **WHEN** an article-scoring, topic-dedup or recap request is built
- **THEN** it carries no `reasoning` block, and takes its non-reasoning behaviour from the model it runs on

#### Scenario: Reasoning text is excluded from the response
- **WHEN** a `reasoning` block is sent
- **THEN** it sets `exclude`, so the provider returns the reasoning tokens' effect but not their text
