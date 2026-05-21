## 1. Database Migration

- [x] 1.1 Add Flyway migration to add `topic_order` INTEGER nullable column to `episode_articles` table

## 2. Composer Topic Ordering

- [x] 2.1 Add `topicOrder: List<String>` field to `CompositionResult`
- [x] 2.2 Add shared topic order extraction logic: parse `|||TOPIC_ORDER|||` block from LLM response, return ordered topic list and cleaned script text
- [x] 2.3 Update `BriefingComposer` prompt to instruct LLM to append topic ordering block after the script; extract topic order in compose method
- [x] 2.4 Update `DialogueComposer` prompt and compose method with same topic ordering logic
- [x] 2.5 Update `InterviewComposer` prompt and compose method with same topic ordering logic
- [x] 2.6 Add tests for topic order extraction (present, missing, malformed)

## 3. Pipeline and Storage

- [x] 3.1 Add `topicOrder: List<String>` field to `PipelineResult`
- [x] 3.2 Pass `CompositionResult.topicOrder` through `LlmPipeline.run()` into `PipelineResult`
- [x] 3.3 Add `topicOrder` column to `EpisodeArticle` entity
- [x] 3.4 Update `EpisodeArticleRepository.insertIgnore()` to accept and store `topicOrder` parameter
- [x] 3.5 Update `EpisodeService.saveEpisodeArticleLinks()` to compute and store `topic_order` by matching each article's topic to its index in `PipelineResult.topicOrder`
- [x] 3.6 Add tests for topic order storage logic

## 4. Sources HTML Generation

- [x] 4.1 Add repository method to query articles with topic and topic_order for an episode, ordered by `topic_order ASC NULLS LAST, relevance_score DESC NULLS LAST`
- [x] 4.2 Update `EpisodeService.generateSourcesFile()` to use the new topic-aware query
- [x] 4.3 Rewrite `EpisodeSourcesGenerator.generate()` to accept articles with topic/order data and render grouped HTML with `<h3>` topic headings under a "Topics Covered" heading
- [x] 4.4 Add fallback: when all articles have null topic_order, render flat list under "Sources" heading
- [x] 4.5 Add tests for topic-grouped HTML generation (with topics, without topics, mixed)

## 5. Recap Topic Awareness

- [x] 5.1 Update `EpisodeRecapGenerator.buildPrompt()` to accept an optional list of topic labels and include them in the prompt
- [x] 5.2 Update `EpisodeService.generateAndStoreRecap()` to pass topic labels from `PipelineResult` to the recap generator
- [x] 5.3 Add tests for recap prompt with and without topic labels

## 6. Verification

- [x] 6.1 Run `mvn test` to verify all existing and new tests pass
- [x] 6.2 Regenerate sources HTML for a recent episode and verify topic grouping renders correctly
