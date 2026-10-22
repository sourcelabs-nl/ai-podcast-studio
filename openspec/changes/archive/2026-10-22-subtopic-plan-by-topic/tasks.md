# Tasks

## 1. Reproduction

- [x] 1.1 Add `SubtopicPlanTest` cases built from episode 303's shape: a story of several articles classified into a rapid-fire subtopic. They fail before the fix (several rapid-fire items for one story) and pass after (one).

## 2. Topic map in the compose context

- [x] 2.1 Add an `articleTopics` map (article id to topic label) to `ComposeContext`, next to `followUpAnnotations`, default empty.
- [x] 2.2 Fill the map in `LlmPipeline.compose` from each `FilteredArticle.topic`. Verified by `LlmPipelineTest` (the composer receives each article's topic).
- [x] 2.3 Fill the map on retry and recompose: `PipelineRunner` passes `LinkedArticlesResult.articleTopics`, built from the topics stored on `episode_articles`. Verified by the updated expected contexts in `PipelineRunnerTest` and `PodcastServiceTest`.

## 3. SubtopicPlan

- [x] 3.1 Assign each story (topic cluster) as a whole to its highest-weight subtopic bucket, ties by the existing bucket name order. Verified by a test where a story split across subtopics lands wholly in the highest-weight bucket.
- [x] 3.2 List one rapid-fire item per topic with the topic label, ranked and capped as now (the cap counts topics). Verified by the story-grouping test.
- [x] 3.3 Add the skip-if-already-covered sentence to the rapid-fire prompt block and name items by label. Verified by a prompt test.
- [x] 3.4 Keep per-article behaviour for an empty map. Verified by a test.
- [x] 3.5 Bundle the rapid-fire budget fraction and cap into `RapidFireSettings` instead of adding a positional parameter.

## 4. Verification

- [x] 4.1 `mvn test` passes (2060 tests).
