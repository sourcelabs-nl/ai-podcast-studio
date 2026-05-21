## MODIFIED Requirements

### Requirement: Two-step LLM pipeline
The system SHALL process articles through a three-stage sequential LLM pipeline: (1) score, summarize, and filter, (2) topic dedup filter, (3) briefing script composition. The pipeline SHALL be triggered by the `BriefingGenerationScheduler` on a configurable cron schedule.

Before running the LLM stages, the pipeline SHALL invoke the `SourceAggregator` to create articles from unlinked posts. For each source belonging to the podcast, the aggregator queries unlinked posts within the configured time window and creates articles (aggregated or 1:1 depending on the source's `aggregate` setting). The resulting articles are persisted to the `articles` table with `post_articles` join entries.

Stage 1 (score+summarize+filter) SHALL process each newly created article with a single LLM call that returns structured JSON containing: `relevanceScore` (integer 0-10) and `summary` (text — a concise summary focusing only on content relevant to the podcast's topic). The system SHALL persist `relevance_score` and `summary` on the article immediately. Articles with `relevanceScore` below the podcast's `relevanceThreshold` SHALL be excluded from composition.

Stage 2 (topic dedup filter) SHALL run after scoring and before composition. The pipeline SHALL pass all relevant unprocessed articles and historical articles from recent GENERATED episodes to the `TopicDedupFilter`. The filter clusters articles by topic, deduplicates against history, selects top 3 per NEW topic, and annotates CONTINUATION topics with follow-up context. The filtered article set and annotations are passed to Stage 3.

Stage 3 (composition) SHALL compose the filtered articles into a briefing script. The composer SHALL receive the dedup filter's output including `[FOLLOW-UP: ...]` annotations for CONTINUATION topics. The composer SHALL NOT receive episode recaps — continuity context comes from the dedup filter annotations. After composition, articles SHALL be marked `is_processed = true`.

Article selection SHALL be delegated to `ArticleEligibilityService`, which applies the article age gate and `isProcessed` checks. The pipeline SHALL NOT query the article repository directly for eligibility.

Both stages SHALL track token usage. Stage 1 SHALL persist token counts on each article. Stage 2 (dedup filter) SHALL track token usage. Stage 3 SHALL return token counts in the `PipelineResult`.

The `PipelineResult` SHALL include a `processedArticleIds` field containing the IDs of all articles that were marked as processed. This enables the caller (`BriefingGenerationScheduler`) to record episode-article links after the episode is persisted.

Stage 1 SHALL use the model resolved for the `filter` stage. Stage 2 SHALL use the model resolved for the `filter` stage. Stage 3 SHALL use the model resolved for the `compose` stage.

#### Scenario: Full pipeline with dedup filter produces a briefing script
- **WHEN** the pipeline runs with 100 relevant candidate articles and 50 historical articles from recent episodes
- **THEN** the dedup filter clusters and deduplicates articles, and only the filtered set (~15-25 articles) is passed to the composer

#### Scenario: Pipeline skipped when no new articles after dedup filter
- **WHEN** the pipeline runs and all candidate articles are duplicates of historical episode articles
- **THEN** no composition LLM call is made and no briefing is generated

#### Scenario: Pipeline with continuation topics
- **WHEN** some candidate articles cover a topic that was in a recent episode but with genuinely new developments
- **THEN** those articles are passed to the composer with `[FOLLOW-UP: ...]` annotations, and the script references previous coverage

#### Scenario: Article age gate prevents old content from new sources
- **WHEN** a new source is added with articles older than the latest published episode
- **THEN** those old articles are excluded by the `ArticleEligibilityService` before reaching the dedup filter

#### Scenario: No relevant articles after scoring
- **WHEN** all scored articles have `relevanceScore` below the podcast's `relevanceThreshold`
- **THEN** no dedup filter call is made, no composition LLM call is made, and no briefing is generated

#### Scenario: PipelineResult contains processed article IDs
- **WHEN** the pipeline composes a briefing from 15 filtered articles
- **THEN** the `PipelineResult.processedArticleIds` contains the IDs of all 15 articles

#### Scenario: Episode-article links recorded after episode creation
- **WHEN** the `BriefingGenerationScheduler` creates an episode from a `PipelineResult`
- **THEN** the scheduler records one `episode_articles` row for each article ID in `PipelineResult.processedArticleIds`
