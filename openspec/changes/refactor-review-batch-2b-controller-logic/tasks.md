# Tasks

## 1. Episode status rules

- [x] 1.1 Add `EpisodeStatusNotAllowedException` and its handler mapping (409, `{"error": message}`), and verify with a handler test
- [x] 1.2 Move the status checks of `editScript`, `approve`, `approvePublication`, `discard`, `retry` and `regenerateAudio` into `EpisodeService`, `EpisodeDiscardService` and `PodcastService.retryEpisode`, keeping every message, and verify each with a service test for the allowed and the rejected status
- [x] 1.3 Remove the checks from `EpisodeController` and update its tests to stub the thrown exception, verifying the same 409 bodies

## 2. Podcast validation and mapping

- [x] 2.1 Add `InvalidPodcastException` (400) and `InvalidComposeSettingsException` (422) with handler mappings, and verify with a handler test
- [x] 2.2 Create `PodcastValidator` with the moved `validateTtsConfig`, timezone, fullBodyThreshold, compose settings, subtopics and rapid-fire checks, and verify with a unit test per rule covering the same messages as before
- [x] 2.3 Move parsing and defaults into the mappers and `Podcast` constants, and verify with mapper tests for defaults and for an unsupported language, provider and style
- [x] 2.4 Slim `PodcastController.create` and `update` to parse, validate and delegate, delete the helpers and `REASONING_EFFORTS`, and verify the existing controller tests still return the same statuses and bodies

## 3. Completion

- [x] 3.1 Run `mvn test`, restart the app, and re-run `/code-review --all` until this batch's findings are gone
