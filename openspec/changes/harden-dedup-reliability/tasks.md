## 1. Non-reasoning dedup model

- [x] 1.1 Change the dedup stage default model in `application.yaml` from `anthropic/claude-sonnet-4.6` to `deepseek/deepseek-v4-flash` and update the explanatory comment
- [x] 1.2 Repoint the affected podcast's per-podcast dedup override from `deepseek/deepseek-v4-pro` to `deepseek/deepseek-v4-flash` via the podcast API

## 2. Bound the dedup historical context

- [x] 2.1 Add `DedupProperties(maxHistoricalArticles = 120)` to `LlmProperties` in `AppProperties.kt` and the `app.llm.dedup.max-historical-articles` default in `application.yaml`
- [x] 2.2 Cap the deduplicated historical list to `maxHistoricalArticles` (most-recent first) in `ArticleEligibilityService.findHistoricalArticles`
- [x] 2.3 Trim the historical block in `TopicDedupFilter.buildPrompt` to titles only (drop summaries)
- [x] 2.4 Truncate each historical title to a fixed max length (150 chars) so an oversized source title cannot dominate the prompt

## 3. Fail the episode on dedup failure

- [x] 3.1 Remove the silent fallback in `LlmPipeline.dedup()` so the exception propagates and the episode fails
- [x] 3.2 Remove the silent fallback in the preview path so the error surfaces to the preview caller

## 4. Tests

- [x] 4.1 Update `TopicDedupFilterTest` for the title-only historical block and oversized-title truncation
- [x] 4.2 Add `ArticleEligibilityServiceTest` coverage for the historical-article cap
- [x] 4.3 Add `LlmPipelineTest` coverage that `dedup()` propagates the exception on filter failure
- [x] 4.4 Run the full test suite (`./mvnw test`) — all green
