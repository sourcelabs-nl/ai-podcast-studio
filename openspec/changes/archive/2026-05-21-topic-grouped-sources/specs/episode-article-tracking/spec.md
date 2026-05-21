## MODIFIED Requirements

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

## ADDED Requirements

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
