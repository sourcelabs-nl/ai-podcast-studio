# Proposal

## Why

The code review of 2026-10-01 found business logic in two controllers. `EpisodeController` encodes the status transitions each action allows. `PodcastController` parses enums, applies the podcast's defaults and runs about 70 lines of validation, duplicated between `create` and `update`, and its validation helpers and constants sit in the controller file. Controllers must validate, delegate and map; the rules belong in services and domain types, where other entry points get them too.

## What Changes

- Episode actions (edit script, approve, approve publication, discard, retry, regenerate audio) check the episode's status in the service that performs the action and throw a typed conflict exception. `PodcastExceptionHandler` maps it to the same 409 body the controller returned.
- Podcast request parsing (language, TTS provider, style) and defaults move into mappers and `Podcast`; validation moves into one `PodcastValidator` shared by `create` and `update`, which throws typed exceptions that `PodcastExceptionHandler` maps to the same 400 and 422 bodies.
- The validation helpers and `REASONING_EFFORTS` leave `PodcastController`.

`PodcastService.create` also stops dropping `rapidFireMaxItems` from the requested podcast, which the update path already honors.

HTTP status codes and response bodies stay identical, so no spec changes.

## Capabilities

### New Capabilities

### Modified Capabilities

## Impact

- `podcast/EpisodeController.kt`, `EpisodeService.kt`, `EpisodeDiscardService.kt`, `PodcastService.kt`, `PodcastExceptions.kt`, `PodcastExceptionHandler.kt`.
- `podcast/PodcastController.kt`, `PodcastMappers.kt`, `PodcastDtos.kt`, a new `PodcastValidator.kt`, and `store/Podcast.kt` (default constants).
- Controller tests that stub a service to produce a 409 or 400 now stub a thrown exception; the status-rule tests move to the service tests.
