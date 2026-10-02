# Design

## Context

See proposal.md. `PodcastExceptionHandler` already maps typed `IllegalStateException` subclasses to 409 for `PodcastController`, `EpisodeController` and `ExperimentController`. The affected episode service methods (`updateScript`, `approveAndGenerateAudio`, `approveForPublication`, `regenerateAudio`, `PodcastService.retryEpisode`, `EpisodeDiscardService.discard`) are called only by `EpisodeController`, so guarding inside them changes no other caller. `PodcastService.create` already applies the podcast defaults; the controller repeats them.

## Goals / Non-Goals

**Goals:** controllers contain no status rules, no defaults and no validation; responses stay byte-identical.

**Non-Goals:** the inline `try/catch` in `regenerateRecap` (a later batch); the SSE `preview` code; changing any validation rule or message.

## Decisions

- **One exception for status conflicts.** `EpisodeStatusNotAllowedException(message)` extends `IllegalStateException`; the handler returns 409 with `{"error": message}` and no `code` field, as the controller did. Each service method throws it with the exact message the controller used, including the discard message that depends on `GENERATING_AUDIO`. In discard the status check stays ahead of the published-target check, as today.
- **Two exceptions for invalid podcasts.** `InvalidPodcastException` maps to 400 and `InvalidComposeSettingsException` to 422, both `{"error": message}`, matching today's split. Alternative: one exception carrying a status, rejected because the handler would then decide nothing.
- **Parsing in the mappers.** `CreatePodcastRequest.toPodcast(userId)` and an update counterpart applying a request to the existing podcast parse language, provider and style, throwing `InvalidPodcastException`, and take defaults from named constants on `Podcast` that `PodcastService.create` also uses, so the literals exist once.
- **`PodcastValidator`.** A Spring component that owns `validateTtsConfig` (moved from `PodcastService`), the timezone, `fullBodyThreshold`, compose settings, subtopics and rapid-fire checks, and throws on the first failure in today's order. On update it validates the merged podcast rather than the request fields. That also re-checks stored values, which are valid by construction.
- **Controller flow.** Parse via the mapper, call `podcastValidator.validate(podcast)`, call the service. Validation before persistence stays a controller-triggered step so callers of `PodcastService.create` that build podcasts directly are unaffected.

## Risks / Trade-offs

- [Merged validation on update rejects a request because of a stored value] -> Stored values were validated when written; a test covers updating a podcast with unrelated fields.
- [Order of errors changes when a request has several] -> The validator keeps the order of the old code (language, provider, style, TTS config, timezone, fullBodyThreshold, compose settings, subtopics, rapid-fire).
