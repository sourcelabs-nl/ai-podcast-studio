## ADDED Requirements

### Requirement: Feed fetch failures preserve their HTTP status

The system SHALL retrieve RSS and YouTube feeds over an HTTP client that raises status-bearing exceptions (`HttpClientErrorException` / `HttpServerErrorException`), so that the HTTP status of a failed fetch reaches `PollFailure.classify` rather than being flattened into a generic `IOException` whose status survives only in its message.

Without this, every RSS and YouTube poll failure classifies as transient regardless of the server's response, which silently defeats both auto-disable and any permanent/transient distinction downstream of classification.

Feed requests SHALL send an explicit `User-Agent` identifying the application, matching the one used by the deep-fetch path.

#### Scenario: RSS feed returning 403 classifies as permanent
- **WHEN** an RSS source's feed URL responds with HTTP 403
- **THEN** the poll failure is classified as permanent and `lastFailureType` is set to `"permanent"`

#### Scenario: RSS feed returning 404 classifies as permanent
- **WHEN** an RSS source's feed URL responds with HTTP 404
- **THEN** the poll failure is classified as permanent

#### Scenario: RSS feed returning 500 classifies as transient
- **WHEN** an RSS source's feed URL responds with HTTP 500
- **THEN** the poll failure is classified as transient

#### Scenario: RSS feed returning 429 classifies as transient
- **WHEN** an RSS source's feed URL responds with HTTP 429
- **THEN** the poll failure is classified as transient

#### Scenario: Malformed feed body still classifies as transient
- **WHEN** an RSS source's feed URL responds with HTTP 200 and a body that is not valid XML
- **THEN** the poll failure is classified as transient, as an XML parse failure

#### Scenario: Feed requests identify the application
- **WHEN** the system fetches any RSS or YouTube feed
- **THEN** the request carries a `User-Agent` header identifying the application

#### Scenario: Feed behind a redirect is followed
- **WHEN** an RSS source's feed URL responds with HTTP 301 pointing at another URL serving the feed
- **THEN** the redirect is followed and the feed's entries are returned, as they were before feed retrieval moved to a status-bearing HTTP client

#### Scenario: Feed encoding detection is preserved
- **WHEN** a feed is served with a non-UTF-8 charset declared in its `Content-Type` header or XML declaration
- **THEN** the feed is decoded using that charset, as it was before feed retrieval moved to a status-bearing HTTP client
