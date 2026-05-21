## ADDED Requirements

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
