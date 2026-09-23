## 1. Telemetry

- [x] 1.1 Add V80 migration with nullable `served_provider` and `reasoning_tokens` on `llm_calls`; verify the Flyway migration tests pass
- [x] 1.2 Read reasoning tokens into `TokenUsage` and the served provider from response metadata; add `TokenUsage.plus`; verify with `TokenUsageTest`
- [x] 1.3 Carry both through `LlmCallRecord`, `LlmCall`, `CachingChatModel`, `requestsForEpisode` and `LlmCallResponse`; verify with `CachingChatModelTelemetryTest` and `LlmCallLatencyServiceTest`
- [x] 1.4 Add `ResolvedModel.telemetryStage`, the `research-plan` reported stage and cost stage; verify the latency report lists it

## 2. Research stage

- [x] 2.1 Add research types (`ResearchQueryPlan`, `BackgroundSource`, `PreComposeResearch`, `ResearchRequest`) and query caps; verify compilation
- [x] 2.2 Implement `ResearchPlanner` (filter model, structured output, cap, subject fallback); verify with `ResearchPlannerTest`
- [x] 2.3 Implement `ResearchSourceRecorder.replace` and `PreComposeResearchService` (subjects, concurrent searches, history lookup, deep-dive gating); verify with `PreComposeResearchServiceTest`

## 3. Compose

- [x] 3.1 Add `ComposeContext.research` and run the stage in `LlmPipeline.compose`, `recompose` and `preview`, taking research calls from it; verify with `LlmPipelineTest`
- [x] 3.2 Replace tool prompt blocks with the background research and previously covered blocks and guidance; verify with the composer tests
- [x] 3.3 Build compose clients without tools and remove `createForCompose`, `HistoryLookupTool`, `ResearchTool`, `ToolBudget` and their tests; update `EvaluationRunProvenance`; verify compilation and the composer tests
- [x] 3.4 Drop the explicit research-source clear from the feedback recompose; verify with `PodcastServiceTest`

## 4. Fixes

- [x] 4.1 Sum usage of attempts discarded by `RoleTagValidationAdvisor` into the compose usage; verify with `RoleTagValidationAdvisorTest`
- [x] 4.2 Strip the topic-order block before validation cleanup; verify with `RoleTagValidationAdvisorTest`
- [x] 4.3 Add the plan's projected cost to the research row of the cost breakdown; verify with the mapper test

## 5. Verification

- [x] 5.1 Run `mvn test` and verify it is green
