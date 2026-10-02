# Design

## Context

See proposal.md. Every podcast-scoped controller method starts with the same lines: look up the user, look up the podcast, compare `podcast.userId`, and for episode routes look up the episode and compare `episode.podcastId`. A miss at any step is a 404 with an empty body. Some controllers check the user first and others the configuration first (the OAuth ones); only the podcast-scoped pattern is in scope.

## Goals / Non-Goals

**Goals:** one definition of "this user may reach this podcast and episode", used by every podcast-scoped controller, with identical responses.

**Non-Goals:** controllers that only check the user (`UserController`, `UserProviderConfigController`, `VoiceController`, `ResearchTestController`, the OAuth controllers), the public feed controller, and any authorization model beyond the existing ownership comparison.

## Decisions

- **A component, not a base class.** `PodcastAccess(userService, podcastService, episodeService)` with `podcast(userId, podcastId): Podcast` and `episode(userId, podcastId, episodeId): Episode`. The episode lookup performs the podcast check first, so every episode route inherits it. Alternative: a controller base class, rejected because it hides the dependency and the controllers already use constructor injection.
- **Not found is an exception.** `ResourceNotFoundException` (in the `api` package, next to the other typed exceptions) is mapped by the existing global `ApiExceptionHandler` to `ResponseEntity.notFound().build()`, so the body stays empty. Controllers keep their return types.
- **Where a controller answers a miss differently** (a body, another status, a check in another order), that route keeps its own code and the design note in the code says why. A helper that only repeats the shared rule is deleted.
- **Check order.** The resolver checks user, podcast ownership, then episode, which is the order the controllers used. A route that validated its input before the lookups keeps that order.

## Risks / Trade-offs

- [A route that returned 404 for a different reason now looks like an ownership miss] -> The mapped status and the empty body are the same, and each route's existing test covers its 404 cases.
- [Controller tests fail for lack of a bean] -> They import `PodcastAccess`, which only needs the services they already mock.
