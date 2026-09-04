## Why

The per-stage reasoning policy shipped earlier today does not work. It sets `OpenAiChatOptions.reasoningEffort`, which Spring AI hands to `openai-java` and which serializes to OpenAI's flat `reasoning_effort` field (`ChatCompletionCreateParams`). OpenRouter's documentation is explicit that reasoning is controlled by a `reasoning` **object** (`effort`, `max_tokens`, `exclude`, `enabled`) and that it does **not** accept `reasoning_effort` as an alias. Every compose call has therefore been sending a field the provider does not read, and the routed provider went on choosing the reasoning budget itself, which is the exact failure the change set out to remove.

The measurements confirm it. The two compose calls made after the change returned 2,988 output tokens for a 12,279-character script and 3,199 for a 13,545-character one. A script of that length is roughly 3,000 tokens, so the output is the script and essentially nothing else: no reasoning happened at either of the two efforts configured.

The same mistake made the effort unobservable. Reasoning comes back in `message.reasoning`, and `openai-java`'s `ChatCompletionMessage` declares no field for it, so the text is dropped into unmapped properties and never surfaces. Reasoning would ordinarily announce its plan before answering, and none of that could ever have reached us.

`none` on the scoring, dedup and recap stages was equally inert. Those stages are safe only because they run on `deepseek-v4-flash`, a model that does not reason, and not because of the flag.

## What Changes

- The reasoning budget moves into the OpenRouter extra body as `reasoning: {effort, exclude}`, next to the `provider` block that is already sent there. The flat field is no longer sent to OpenRouter at all: combined with `require_parameters` it is an unsupported parameter and could steer routing on a field the provider never reads.
- A direct `openai` model keeps the flat `reasoning_effort`, where it is the correct field. The one helper now applies whichever form the resolved provider actually reads.
- An effort of `none` sends no `reasoning` block at all, rather than `effort: "none"`. `require_parameters` restricts routing to endpoints supporting every parameter supplied, so asking a deliberately non-reasoning model to acknowledge a reasoning parameter risks leaving no eligible endpoint, and the filter and dedup stages already get their behaviour from the model they run on. This replaces the previous requirement that those stages request `none`.
- `exclude` keeps the reasoning text out of the response. The tokens are billed either way and the client has no field to read it from, and leaving it out means it cannot be mistaken for the script, which fits the compose responses that arrived with no speaker tags at all.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `cost-tracking`: the per-stage reasoning requirement is restated in the form OpenRouter reads, and the structured stages no longer send an inert flag.
- `article-dedup-filter`: its reasoning claim is corrected — the non-reasoning model is what protects the stage.

## Impact

- Backend: `OpenRouterRouting.extraBodyFor` takes the effort; `withOpenRouterFloor` becomes `withRoutingAndReasoning`; `ComposerUtils.buildComposeOptions`, `ArticleScoreSummarizer`, `TopicDedupFilter`, `EpisodeRecapGenerator`.
- The per-podcast `composeSettings.reasoningEffort` and `app.compose.reasoning-effort` keep their meaning; only the wire form changes, so no configuration moves.
- Reasoning tokens are billed as output tokens and already recorded per episode as `compose_output_tokens`, so whether the effort now takes effect is readable from the next generation without new instrumentation.
- No schema, API or frontend change.
