# Design

## Context

See proposal.md. `PodcastExceptionHandler` and `PublishingExceptionHandler` already map typed exceptions for their own controllers and deliberately have no catch-all. The inline catches in the other controllers pick a status by exception base type (`IllegalArgumentException`, `IllegalStateException`, `Exception`), which is how the same type ended up with different statuses.

## Goals / Non-Goals

**Goals:** one error contract and one place that maps it; no controller builds an error response; no behavior change in status or body.

**Non-Goals:** a catch-all handler (unexpected exceptions keep Spring's default), changing the existing podcast and publishing handlers, moving the SSE streaming error events, adding error codes beyond what exists.

## Decisions

- **Typed exceptions carry the status.** `InvalidRequestException` (400) and `UnprocessableRequestException` (422) extend `IllegalArgumentException`; `UpstreamServiceException` (502) and `OperationFailedException` (500) extend `RuntimeException`. The 400/422 split follows the specs: source validation is 422, everything else that was `IllegalArgumentException` stays 400. Alternative: one exception with a status field, rejected because the handler would then decide nothing and a throw site could pick an arbitrary status.
- **The message is the complete client message**, built at the throw site so the existing texts stay byte-identical (for example `Failed to fetch voices from ElevenLabs: <detail>`, `Backup failed: <detail>`).
- **Services wrap, controllers do not.** The service that knows an operation failed throws `OperationFailedException` (with the cause) and logs it once; the advice only maps. Voice discovery moves into a `VoiceDiscoveryService` that wraps the two API clients, so the controller stops calling clients.
- **Advice scope.** A single `ApiExceptionHandler` with no `assignableTypes` for these four types, at the lowest precedence so the scoped handlers keep priority. It handles only these types.
- **Cancellation.** The suspend call sites rethrow `CancellationException` by not catching it at all, since the controller no longer catches.
- **`ResearchTestController`.** `ResearchService` gains `testTavily(userId): ResearchTestResult`, containing the HTTP call and its own failure handling (it already answers 200 with `success = false`).
- **Streaming code is untouched.** `PreviewAudioController`'s and `PodcastController`'s SSE error events are the protocol of those endpoints, not an exception mapping.

## Risks / Trade-offs

- [A service that now throws a subclass is also called from code that catches the base type] -> Subclassing keeps those catches working; tests cover the callers found by a reference search.
- [A throw site changes a message] -> Each throw site is covered by a test asserting the exact text, and the controller tests assert status and body.
