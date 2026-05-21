# Capability: Episode-Article Tracking

## Purpose

Tracks which articles contributed to each episode, providing traceability from episodes back to their source articles.
## Requirements
### Requirement: Episode-article join table
The system SHALL maintain an `episode_articles` join table with columns: `id` (auto-generated INTEGER PRIMARY KEY), `episode_id` (INTEGER, NOT NULL, FK to episodes), `article_id` (INTEGER, NOT NULL, FK to articles), `topic` (TEXT, nullable), `topic_order` (INTEGER, nullable). A unique constraint SHALL exist on `(episode_id, article_id)` to prevent duplicate linkage. The `topic` column stores the dedup cluster topic label that groups related articles. The `topic_order` column stores the discussion order of the topic within the episode script (0-based index), enabling sources pages to display topics in the order they are discussed. This table enables traceability from episodes back to the articles that contributed to them.

Episode-article links SHALL be saved for ALL episode generation paths, including both the scheduled generation (`BriefingGenerationScheduler`) and manual generation (`PodcastController.generate()`). Both paths SHALL delegate to shared logic in `EpisodeService` to save episode-article links.

#### Scenario: Articles linked to episode with topic order
- **WHEN** a briefing is composed from 8 articles across 4 dedup clusters and the composer outputs a topic ordering of ["Topic A", "Topic B", "Topic C", "Topic D"]
- **THEN** 8 rows are created in `episode_articles`, each with the corresponding cluster topic label and `topic_order` set to the index of that topic in the composer's ordering (0, 1, 2, or 3)

#### Scenario: Topic order is null for recomposed episodes
- **WHEN** an episode is recomposed (bypassing the dedup filter)
- **THEN** rows are created in `episode_articles` with `topic` and `topic_order` both set to NULL

#### Scenario: Duplicate linkage prevented
- **WHEN** an `(episode_id, article_id)` combination already exists in `episode_articles`
- **THEN** the duplicate insert is rejected

### Requirement: Episode-article repository
The system SHALL provide a Spring Data JDBC repository for the `EpisodeArticle` entity with methods to save links and query articles by episode.

#### Scenario: Save episode-article links in bulk
- **WHEN** an episode is created from 3 articles
- **THEN** the repository saves 3 `EpisodeArticle` records

#### Scenario: Find articles for an episode
- **WHEN** querying articles for episode ID 42
- **THEN** the repository returns all article IDs linked to episode 42

### Requirement: Pipeline returns processed article IDs
The `LlmPipeline` SHALL return the list of processed article IDs in `PipelineResult` along with a mapping of article ID to topic label (`articleTopics: Map<Long, String>`). The topic mapping SHALL be built from the `DedupResult.clusters` by iterating each cluster's `selectedArticleIds` and mapping them to the cluster's `topic` string.

#### Scenario: PipelineResult includes article IDs and topics
- **WHEN** the LLM pipeline processes 5 articles across 3 dedup clusters into a briefing
- **THEN** the `PipelineResult` contains the IDs of all 5 processed articles and a topic map with 5 entries mapping each article ID to its cluster topic

#### Scenario: PipelineResult with no articles
- **WHEN** the LLM pipeline finds no relevant articles and returns null
- **THEN** no `PipelineResult` is returned (null)

#### Scenario: Recompose pipeline result has empty topic map
- **WHEN** the recompose pipeline produces a `PipelineResult`
- **THEN** the `articleTopics` map is empty

### Requirement: Shared episode creation logic in EpisodeService
The system SHALL provide a method in `EpisodeService` that encapsulates the post-pipeline episode creation logic: saving the episode, saving episode-article links, marking articles as processed, generating a recap, and updating `lastGeneratedAt` on the podcast. Both `BriefingGenerationScheduler` and `PodcastController.generate()` SHALL delegate to this shared method instead of reimplementing the logic independently.

Articles SHALL only be marked as `is_processed = true` AFTER being successfully linked to the episode via `episode_articles`. The `LlmPipeline` SHALL NOT mark articles as processed; this responsibility belongs exclusively to `EpisodeService.createEpisodeFromPipelineResult()`.

The system SHALL NOT advance `lastGeneratedAt` when the pipeline returns no results (null). Only the successful creation of an episode SHALL update this timestamp. This ensures that articles published before the timestamp remain visible in the upcoming view even when a pipeline run finds no relevant content to compose.

#### Scenario: Scheduler delegates to shared creation logic
- **WHEN** `BriefingGenerationScheduler` completes a pipeline run with a non-null result
- **THEN** it calls the shared `EpisodeService` method which saves the episode, links articles, marks articles as processed, generates recap, and updates `lastGeneratedAt`

#### Scenario: Manual generate delegates to shared creation logic
- **WHEN** `PodcastController.generate()` completes a pipeline run with a non-null result
- **THEN** it calls the same shared `EpisodeService` method which saves the episode, links articles, marks articles as processed, generates recap, and updates `lastGeneratedAt`

#### Scenario: Episode recap generated for manually triggered episodes
- **WHEN** a manual generation produces an episode
- **THEN** a recap is generated and stored on the episode, matching the behavior of scheduled generation

#### Scenario: Pipeline returns no results
- **WHEN** `llmPipeline.run()` returns null (no relevant articles to compose)
- **THEN** the system SHALL NOT update `lastGeneratedAt` on the podcast

#### Scenario: Pipeline returns results and episode is created
- **WHEN** `llmPipeline.run()` returns a `PipelineResult` and an episode is successfully created
- **THEN** `lastGeneratedAt` SHALL be updated to the current timestamp

#### Scenario: Articles marked processed only after linking
- **WHEN** the pipeline composes a briefing from 5 articles and an episode is created
- **THEN** all 5 articles are linked to the episode via `episode_articles` first, and only then marked as `is_processed = true`

#### Scenario: Pipeline does not mark articles as processed
- **WHEN** `LlmPipeline.run()` completes composition
- **THEN** the pipeline SHALL NOT set `is_processed = true` on any article; it returns the article IDs in `PipelineResult` for the caller to handle

### Requirement: Pipeline returns topic ordering
The `LlmPipeline` SHALL return the ordered list of topic labels in `PipelineResult` as `topicOrder: List<String>`. The topic ordering SHALL be extracted from the composer's output (appended as a delimited JSON block after the script text). `EpisodeService` SHALL use this list to assign `topic_order` values when saving episode-article links by matching each article's topic label to its index in the `topicOrder` list.

#### Scenario: PipelineResult includes topic ordering
- **WHEN** the LLM pipeline processes 8 articles across 4 clusters and the composer outputs topic ordering ["AI Safety", "Code Quality", "New Releases", "Tooling"]
- **THEN** the `PipelineResult` contains `topicOrder` with those 4 labels in order

#### Scenario: Composer omits topic ordering
- **WHEN** the composer LLM response does not contain the topic order delimiter block
- **THEN** `PipelineResult.topicOrder` SHALL be an empty list and all `topic_order` values SHALL be null

#### Scenario: Recompose pipeline has empty topic ordering
- **WHEN** the recompose pipeline produces a `PipelineResult`
- **THEN** the `topicOrder` list is empty

### Requirement: Composer outputs topic ordering metadata
All composer classes (BriefingComposer, DialogueComposer, InterviewComposer) SHALL instruct the LLM to append a topic ordering block after the script text. The block SHALL use the format:
```
|||TOPIC_ORDER|||
["topic 1", "topic 2", ...]
|||END_TOPIC_ORDER|||
```
The composer SHALL parse this block from the response, extract the ordered topic list, and remove the block from the script text. The topic labels in the ordering SHALL correspond to the topic labels assigned by the `TopicDedupFilter`.

#### Scenario: Composer extracts topic ordering from LLM response
- **WHEN** the LLM outputs a script followed by the topic order block with 5 topics
- **THEN** `CompositionResult.topicOrder` contains 5 topic labels in order and the script text does not contain the delimiter block

#### Scenario: Composer handles missing topic order block
- **WHEN** the LLM output does not contain the `|||TOPIC_ORDER|||` delimiter
- **THEN** `CompositionResult.topicOrder` is an empty list and the script text is returned as-is

### Requirement: Query articles with topic ordering
The system SHALL provide a repository method to query articles for an episode including their `topic` and `topic_order` from the `episode_articles` join table. Results SHALL be ordered by `topic_order ASC NULLS LAST, relevance_score DESC NULLS LAST`.

#### Scenario: Query articles grouped by topic
- **WHEN** querying articles for an episode with 10 articles across 3 topics
- **THEN** the results include each article's title, URL, topic label, and topic_order, ordered by topic_order then relevance score

