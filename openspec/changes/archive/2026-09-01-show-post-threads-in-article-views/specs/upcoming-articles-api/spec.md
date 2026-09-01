## MODIFIED Requirements

### Requirement: Upcoming articles endpoint
The system SHALL provide `GET /users/{userId}/podcasts/{podcastId}/upcoming-articles` returning the unprocessed articles that would feed the next episode, together with their sources and counts.

Each article SHALL include `postCount`, the number of posts it was aggregated from, matching the episode articles endpoint so both views can show a thread's size. An article that is not an aggregate SHALL report a `postCount` of 1.

#### Scenario: Upcoming articles returned with sources
- **WHEN** a request is made for a podcast with unprocessed articles
- **THEN** the response lists the articles with their source details and counts

#### Scenario: Aggregated upcoming article reports its thread size
- **WHEN** an upcoming article was aggregated from 8 posts
- **THEN** its `postCount` is 8
