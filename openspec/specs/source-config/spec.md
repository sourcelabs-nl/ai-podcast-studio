# Capability: Source Configuration

## Purpose

Configuration loading for content sources and application settings, using Spring's configuration properties to define sources, topics, and operational parameters.
## Requirements
### Requirement: Source configuration via YAML
The system SHALL manage content source definitions in the database via a REST API. Each source entity SHALL have the fields: `id` (unique string), `type` (one of `rss`, `website`, `twitter`, `youtube`), `url`, `pollIntervalMinutes` (default: 30), `enabled` (default: true), `aggregate` (nullable boolean, default: null — enabling hybrid auto-detect/override for article aggregation), `pollDelaySeconds` (nullable integer, default: null — per-source override for delay between polls to the same host), `categoryFilter` (nullable string, default: null — comma-separated list of category terms for RSS pre-ingestion filtering), and `label` (nullable string, default: null — human-readable label for display).

#### Scenario: Source created via API
- **WHEN** a `POST /users/{userId}/podcasts/{podcastId}/sources` request is received with a valid source definition
- **THEN** the source is persisted in the database with the configured values

#### Scenario: Source with default values
- **WHEN** a source entry omits `pollIntervalMinutes`, `enabled`, `aggregate`, `pollDelaySeconds`, `categoryFilter`, and `label`
- **THEN** the source defaults to a 30-minute poll interval, enabled = true, aggregate = null (auto-detect), pollDelaySeconds = null (use global/host defaults), categoryFilter = null (no category filtering), and label = null

#### Scenario: Disabled source is loaded but not active
- **WHEN** a source has `enabled: false`
- **THEN** the source exists in the database but is excluded from polling

#### Scenario: Source with explicit aggregate override
- **WHEN** a source has `aggregate: true`
- **THEN** articles from this source are always aggregated regardless of type or URL

#### Scenario: Source with explicit poll delay override
- **WHEN** a source has `pollDelaySeconds: 5`
- **THEN** a 5-second delay is applied after polling this source, overriding any host or type default

#### Scenario: RSS source with category filter
- **WHEN** a source has `type: "rss"` and `categoryFilter: "kotlin,AI"`
- **THEN** the source is configured with category filter terms `["kotlin", "AI"]` for pre-ingestion filtering

### Requirement: Article count statistics in source list response
The source list API (`GET /users/{userId}/podcasts/{podcastId}/sources`) SHALL include article count statistics for each source: `articleCount` (total articles from this source), `relevantArticleCount` (articles with relevance score >= podcast's relevance threshold), and `postCount` (total posts from this source).

#### Scenario: Source with articles
- **WHEN** a source has 42 articles, 18 of which have relevance_score >= the podcast's threshold, and 120 posts
- **THEN** the source response SHALL include `articleCount: 42`, `relevantArticleCount: 18`, and `postCount: 120`

#### Scenario: Source with no articles
- **WHEN** a source has no articles in the articles table and no posts in the posts table
- **THEN** the source response SHALL include `articleCount: 0`, `relevantArticleCount: 0`, and `postCount: 0`

#### Scenario: Batch computation
- **WHEN** the source list is fetched for a podcast with multiple sources
- **THEN** article counts and post counts SHALL each be computed in a single batch query, not per-source

### Requirement: Topic configuration
The system SHALL store a `topic` string on the `Podcast` entity that defines the user's area of interest. This topic is used as context for LLM relevance filtering.

#### Scenario: Topic stored on podcast
- **WHEN** a podcast is created or updated with `topic: "AI engineering, LLM applications"`
- **THEN** the topic string is available for injection into LLM prompts

### Requirement: Application configuration via application.yml
The system SHALL read application settings from `application.yml` including: OpenRouter API key and base URL (via Spring AI properties), OpenAI TTS API key, TTS voice and model, briefing cron schedule, briefing target word count, episode storage directory, episode retention days, and podcast feed metadata (base URL, title).

#### Scenario: API keys from environment variables
- **WHEN** `application.yml` references `${OPENROUTER_API_KEY}` and `${OPENAI_API_KEY}`
- **THEN** the system resolves these from environment variables at startup

#### Scenario: Briefing schedule configuration
- **WHEN** `application.yml` contains `app.briefing.cron: "0 0 6 * * *"`
- **THEN** the briefing generation pipeline runs daily at 6 AM

### Requirement: Validate source URL on creation
When a source is created, the system SHALL perform a test fetch of the URL and verify that parseable content is returned before persisting the source. If validation fails, the source SHALL NOT be created and the system SHALL return HTTP 422 with a descriptive error message.

Validation SHALL be source-type-aware:
- **RSS sources**: The system SHALL fetch the URL and verify the response contains valid RSS/Atom XML with at least one item/entry
- **Website sources**: The system SHALL fetch the URL and verify the response contains extractable text content
- **Twitter sources**: The system SHALL skip URL validation (Twitter uses OAuth API, not direct URL fetch)
- **Reddit/YouTube sources**: The system SHALL skip URL validation for now

#### Scenario: RSS source with valid feed
- **WHEN** a source is created with type `rss` and a URL that returns valid RSS XML with 5 items
- **THEN** the source is created successfully and returned with HTTP 201

#### Scenario: RSS source with empty feed
- **WHEN** a source is created with type `rss` and a URL that returns HTTP 200 with an empty body
- **THEN** the system returns HTTP 422 with message "RSS feed at URL returned no content"

#### Scenario: RSS source with invalid XML
- **WHEN** a source is created with type `rss` and a URL that returns HTML instead of RSS XML
- **THEN** the system returns HTTP 422 with message "URL does not appear to be a valid RSS/Atom feed"

#### Scenario: RSS source with unreachable URL
- **WHEN** a source is created with type `rss` and a URL that times out or returns a connection error
- **THEN** the system returns HTTP 422 with message "Could not reach URL: <error detail>"

#### Scenario: Website source with valid content
- **WHEN** a source is created with type `website` and a URL that returns an HTML page with extractable text
- **THEN** the source is created successfully

#### Scenario: Website source with empty content
- **WHEN** a source is created with type `website` and a URL that returns an empty response
- **THEN** the system returns HTTP 422 with message "Website at URL returned no extractable content"

#### Scenario: Twitter source skips validation
- **WHEN** a source is created with type `twitter`
- **THEN** no URL validation is performed and the source is created directly

