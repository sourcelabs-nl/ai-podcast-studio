# Capability: SSE Preview

## Purpose

Server-Sent Events endpoint for the preview pipeline, providing real-time progress feedback and avoiding proxy timeouts.

## Requirements
### Requirement: SSE preview endpoint
The system SHALL provide a `GET /users/{userId}/podcasts/{podcastId}/preview` endpoint that returns `text/event-stream` content type, running the preview pipeline and streaming progress events followed by the final result.

#### Scenario: Successful preview with progress events
- **WHEN** a GET request is made to `/users/{userId}/podcasts/{podcastId}/preview`
- **THEN** the server opens an SSE stream, emits `progress` events as the pipeline moves through stages (aggregating, scoring, composing), emits a `result` event with `{scriptText, style, articleIds}` when complete, and emits a `complete` event to signal end of stream

#### Scenario: Preview with no relevant articles
- **WHEN** the preview pipeline finds no relevant articles after scoring
- **THEN** the server emits a `result` event with `{message: "No relevant articles available for preview"}` and a `complete` event

#### Scenario: Preview pipeline error
- **WHEN** an error occurs during the preview pipeline
- **THEN** the server emits an `error` event with `{message: "<error description>"}` and completes the stream

#### Scenario: Invalid podcast or user
- **WHEN** the user or podcast is not found, or the podcast does not belong to the user
- **THEN** the server returns HTTP 404 (not an SSE stream)

### Requirement: SSE progress events
The preview SSE stream SHALL emit structured progress events at each pipeline stage transition, covering aggregation, scoring, deduplication and composition.

#### Scenario: Aggregation stage
- **WHEN** the pipeline begins aggregating unlinked posts
- **THEN** a `progress` event is emitted with `{stage: "aggregating"}`

#### Scenario: Scoring stage
- **WHEN** the pipeline begins scoring unscored articles
- **THEN** a `progress` event is emitted with `{stage: "scoring", articleCount: N}` where N is the number of articles to score

#### Scenario: Scoring progress
- **WHEN** articles are scored
- **THEN** further `progress` events are emitted with `{stage: "scoring", articleCount: N, scoredCount: M}` as M advances toward N

#### Scenario: Deduplication stage
- **WHEN** the pipeline begins the topic dedup filter
- **THEN** a `progress` event is emitted with `{stage: "deduplicating", articleCount: N}` where N is the number of eligible articles entering the filter

#### Scenario: Composing stage
- **WHEN** the pipeline begins composing the script
- **THEN** a `progress` event is emitted with `{stage: "composing", articleCount: N}` where N is the number of relevant articles being composed

#### Scenario: No unscored articles
- **WHEN** all articles are already scored (no scoring needed)
- **THEN** the scoring progress event is skipped and the pipeline proceeds directly to deduplication

### Requirement: SSE emitter configuration
The SseEmitter SHALL be configured with a 5-minute timeout to accommodate large pipeline runs.

#### Scenario: Timeout before completion
- **WHEN** the pipeline takes longer than 5 minutes
- **THEN** the SseEmitter times out and the connection is closed

### Requirement: Next.js SSE proxy route
The frontend SHALL provide a custom Next.js API route at `/api/users/[userId]/podcasts/[podcastId]/preview/route.ts` that proxies the SSE stream from the backend without buffering.

#### Scenario: SSE stream passthrough
- **WHEN** the frontend makes a GET request to `/api/users/{userId}/podcasts/{podcastId}/preview`
- **THEN** the Next.js API route forwards the request to the backend, streams the response back to the client with `Content-Type: text/event-stream`, and does not buffer the response

