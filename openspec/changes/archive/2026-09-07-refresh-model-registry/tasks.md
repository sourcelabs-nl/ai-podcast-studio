## 1. Establish what can actually route

- [x] 1.1 Pull the live OpenRouter catalogue and rank candidates by Artificial Analysis index within glm-5.3's price band; verify by identifying which models beat it (only `openai/gpt-5.6-sol:batch`, a batch variant)
- [x] 1.2 For each candidate, probe the live API with the production `provider` block and an explicit `effort: "none"`; verify which route (deepseek flash family, granite-4.2-8b, nemotron-3.5-lightning, the GLM family, deepseek-v4-pro) and which 404
- [x] 1.3 Establish why the proprietary models fail, via `GET /api/v1/models/{id}/endpoints`; verify the cause is `quantization: "unknown"` on every vendor-native endpoint (gpt-6-astra 5/5, claude-fable-5.1 4/4, gemini-3.8-flash 6/6)
- [x] 1.4 Compare `deepseek/deepseek-v4-flash` against `deepseek/deepseek-v4-flash-0731`; verify the July release wins on every index and is cheaper at the eligible floor
- [x] 1.5 Corroborate the compose choice against human-preference data for prose, which the reasoning indices do not measure; verify GLM-5.3 leads the open-weight creative-writing ranking with qwen3.8-max tied but pricier and itself floor-blocked

## 2. Stage defaults

- [x] 2.1 Point `app.llm.defaults` filter and dedup at `deepseek/deepseek-v4-flash-0731` and compose at `z-ai/glm-5.3`, with comments carrying the benchmark and routing rationale; verify `GET /config/defaults` reports all three after a restart
- [x] 2.2 Sync `StageDefaults` in `AppProperties.kt` to the same three models and document the routing constraint; verify `mvn test` passes and the yaml and code no longer disagree

## 3. Pricing

- [x] 3.1 Refresh the five drifted prices to the cheapest endpoint clearing the floor and add an entry for `deepseek/deepseek-v4-flash-0731`; verify each against `/api/v1/models` and `/api/v1/models/{id}/endpoints` and record the eligible-endpoint counts in the comments

## 4. Withhold the unroutable models from the picker

- [x] 4.1 Add `selectable: Boolean = true` to `ModelCost`, documenting why an unusable entry keeps its pricing; verify `mvn -o compile` succeeds
- [x] 4.2 Add `toSelectableModels` in a new `ConfigMappers.kt` and delegate from `ConfigController`, keeping the filter out of the controller per the architecture rules; verify `ConfigMappersTest` covers withholding, the default, an emptied provider key and the type mapping
- [x] 4.3 Mark the six vendor-native entries `selectable: false`; verify exactly those six carry the flag and `availableModels.openrouter` lists only the six routable models after a restart
- [x] 4.4 Add a `ModelResolverTest` case asserting an unselectable model still resolves its cost; verify it fails if the entry were deleted rather than flagged

## 5. Verification

- [x] 5.1 Run `mvn test`; verify the whole suite passes (1325 tests)
- [x] 5.2 Restart and read `GET /config/defaults`; verify the defaults and the filtered picker, and that TTS providers are unaffected
- [x] 5.3 Check whether any existing episode references a withheld model; verify none do, so the pricing retention is precautionary rather than load-bearing today
- [x] 5.4 Run the code reviewer and fix any violations, repeating until clean per the project's code review loop; verify it reports no violations (0 violations, 0 warnings, 4 notes)
