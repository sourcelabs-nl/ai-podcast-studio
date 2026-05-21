## Why

When adding a new source, there is no validation that the URL actually returns parseable content. A source with a broken or wrong URL (e.g., a nitter RSS feed that returns empty 200 responses) silently fails to produce any posts, and the user only discovers this later when no content appears. Validating the URL on add prevents misconfigured sources from entering the system.

## What Changes

- When creating a source, validate the URL by performing a test fetch and verifying parseable content is returned
- Return a clear error message if validation fails (e.g., "RSS feed returned no items", "URL returned empty response")
- Validation is source-type-aware: RSS feeds must return valid XML with items, websites must return extractable content

## Capabilities

### New Capabilities

_(none — this extends existing capabilities)_

### Modified Capabilities

- `source-config`: Add URL validation on source creation — test fetch and verify parseable content before persisting

## Impact

- `SourceService.create()` — add validation step before saving
- `SourceController` — return 422 with error details on validation failure
- `RssFeedFetcher` — expose a validate/test method (or reuse existing fetch)
- `WebsiteFetcher` — same
- Tests — add validation success/failure test cases
