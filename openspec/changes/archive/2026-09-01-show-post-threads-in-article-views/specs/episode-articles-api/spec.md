## MODIFIED Requirements

### Requirement: Episode articles endpoint
The system SHALL provide `GET /users/{userId}/podcasts/{podcastId}/episodes/{episodeId}/articles` returning the articles linked to an episode with their source details, ordered by relevance score descending.

Each article SHALL include `postCount`, the number of posts it was aggregated from. Short-form sources are aggregated into one article per author thread, so this is the only way a client can tell a single tweet from a fourteen-post conversation; the posts themselves are joined into the article body with no structure a client could rely on. An article that is not an aggregate SHALL report a `postCount` of 1.

#### Scenario: Articles returned with source details
- **WHEN** a request is made for an episode with linked articles
- **THEN** each article includes its title, url, author, publishedAt, relevanceScore, summary, body, subtopic and source

#### Scenario: Aggregated article reports its thread size
- **WHEN** an article was aggregated from 14 posts
- **THEN** its `postCount` is 14

#### Scenario: Non-aggregated article reports one post
- **WHEN** an article was created from a single post, or from a source that is not aggregated
- **THEN** its `postCount` is 1

#### Scenario: Episode with no articles
- **WHEN** a request is made for an episode with no linked articles
- **THEN** an empty list is returned

## ADDED Requirements

### Requirement: Article posts endpoint
The system SHALL provide `GET /users/{userId}/podcasts/{podcastId}/articles/{articleId}/posts` returning the posts an article was aggregated from, ordered by `publishedAt` ascending so a thread reads in the order it was written. Each post SHALL include its id, title, body, url and `publishedAt`.

The endpoint SHALL return 404 when the user, podcast or article does not exist, and when the article belongs to a source of a different podcast, so article contents follow the same ownership rules as every other read.

The posts are served on demand rather than embedded in the article list responses: the article body already contains the same text, so embedding them would roughly double list payloads that are dominated by bodies, and the upcoming-articles view can list far more articles than a single episode.

#### Scenario: Posts returned oldest first
- **WHEN** an article was aggregated from a thread of 3 posts
- **THEN** the endpoint returns all 3 posts ordered by `publishedAt` ascending

#### Scenario: Non-aggregated article returns its single post
- **WHEN** an article was created from one post
- **THEN** the endpoint returns that one post

#### Scenario: Article belonging to another podcast is not readable
- **WHEN** the requested article belongs to a source of a different podcast
- **THEN** the system returns HTTP 404

#### Scenario: Unknown article
- **WHEN** the requested article does not exist
- **THEN** the system returns HTTP 404
