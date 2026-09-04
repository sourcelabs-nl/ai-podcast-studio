## 1. Provider quality floor

- [x] 1.1 Add `OpenRouterRouting` with the accepted quantizations and `require_parameters`, returning the block only for the `openrouter` provider
- [x] 1.2 Document the 25-endpoint spread that makes the floor necessary, and that it leaves 9 / 11 eligible endpoints
- [x] 1.3 Apply it via `extraBody` in compose, scoring, dedup and recap

## 2. Per-stage reasoning

- [x] 2.1 Add `reasoningEffort` to `ComposeProperties` (default `medium`) and surface it in `application.yaml`
- [x] 2.2 Set the configured effort on compose requests
- [x] 2.3 Set `none` on scoring and recap; dedup already sets it
- [x] 2.4 Record why reasoning belongs to composition and not to the structured stages
- [x] 2.5 Let a podcast override the effort via `composeSettings.reasoningEffort`, falling back to the configured default when absent or blank
- [x] 2.6 Reject an unknown effort in `validateComposeSettings` with 422, and document the key in the Composer Settings editor description

## 3. Compose retry

- [x] 3.1 Add a `compose` retry instance limited to `OpenAIInvalidDataException` and `ResourceAccessException`
- [x] 3.2 Wrap the composer dispatch in `LlmPipeline.compose` with it
- [x] 3.3 Note why a tag-validation failure must not trigger it

## 4. Correct the stale comment

- [x] 4.1 Replace "Z.ai is the only provider serving GLM-5.3" with what the routing floor guarantees

## 5. Tests

- [x] 5.1 `OpenRouterRouting`: the block is produced for openrouter and omitted otherwise
- [x] 5.2 `OpenRouterRouting`: the accepted quantizations exclude fp4 and unknown
- [x] 5.3 Compose options carry the configured reasoning effort and the routing floor
- [x] 5.4 Scoring, dedup and recap options carry `none`
- [x] 5.5 The compose retry fires on `OpenAIInvalidDataException` and not on a tag-validation failure
- [x] 5.6 A podcast's `composeSettings.reasoningEffort` overrides the configured default, and a blank value does not
- [x] 5.7 The create endpoint rejects an unknown `reasoningEffort` with 422 and accepts a documented one
- [x] 5.8 Run `mvn test` and confirm the whole suite passes

## 6. Verify against the real pipeline

- [ ] 6.1 Restart and run a real generation; confirm it composes
- [ ] 6.2 Record compose output tokens, duration and cost so the reasoning effort can be tuned on evidence
