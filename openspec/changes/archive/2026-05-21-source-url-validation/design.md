## Context

Sources are added via `POST /users/{userId}/podcasts/{podcastId}/sources`. Currently the URL is stored without any validation. Broken URLs (empty responses, invalid XML, unreachable hosts) are only discovered at polling time, and even then they may not surface clearly — an empty RSS response returns 0 posts with no error.

## Goals / Non-Goals

**Goals:**
- Validate that a source URL returns parseable content before persisting
- Return actionable error messages on validation failure
- Keep validation fast — a single test fetch, not a full poll cycle

**Non-Goals:**
- Validating Twitter/X sources (they use OAuth, not direct URL fetch)
- Guaranteeing the feed will always work (content may change after validation)
- Validating content relevance (that's the scorer's job)

## Decisions

### 1. Validate in SourceService.create(), not the controller

**Decision:** The validation logic lives in `SourceService.create()` and throws an `IllegalArgumentException` on failure. The controller catches it and returns 422. This keeps the controller thin and the validation reusable.

### 2. Reuse existing fetcher classes for validation

**Decision:** Call the existing `RssFeedFetcher.fetch()` or `WebsiteFetcher.fetch()` with the URL and check that the result is non-empty. No need for separate validate methods — a fetch that returns 0 items is the validation failure.

**Alternative considered:** Adding a lightweight `validate()` method. Rejected because the full fetch is fast enough and tests the actual parsing path.

### 3. Skip validation for Twitter sources

**Decision:** Twitter sources use OAuth and the X API, not direct URL fetch. Validation would require authenticated API calls which adds complexity. Skip for now — Twitter sources fail visibly with OAuth errors.

### 4. Return 422 Unprocessable Entity with error details

**Decision:** Use HTTP 422 (not 400) to distinguish "syntactically valid request but semantically invalid URL" from "malformed request body." Include a `message` field with the specific failure reason.

## Risks / Trade-offs

- **Slow external fetch on add**: A test fetch adds latency to source creation (~1-5s). Acceptable since source creation is infrequent. → No mitigation needed.
- **Transient failures**: A feed may be temporarily down during validation but work later. → The user can retry. The validation prevents obviously broken URLs, not transient issues.
