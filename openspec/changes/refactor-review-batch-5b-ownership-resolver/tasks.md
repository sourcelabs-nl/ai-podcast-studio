# Tasks

## 1. Resolver

- [x] 1.1 Add `PodcastAccess`, `ResourceNotFoundException` and its mapping in `ApiExceptionHandler`, and verify with unit tests for an unknown user, an unknown podcast, a podcast of another user, an unknown episode and an episode of another podcast, and a MockMvc test that the response is 404 with an empty body

## 2. Controllers

- [x] 2.1 Use `PodcastAccess` in `EpisodeController` and `PodcastController`, and verify their existing tests pass with the same statuses and bodies
- [x] 2.2 Use it in `SourceController`, `PodcastImageController`, `PodcastPublicationTargetController`, `PublishingController`, `PublishingTestController`, `PodcastPublicationsController` and `PlaylistController`, and verify the existing tests pass
- [x] 2.3 Replace the local variants in `PreviewAudioController`, `ExperimentController`, `ScriptMetricsController` and `EpisodeScoringController`, and verify the existing tests pass and that no controller still compares `podcast.userId` or `episode.podcastId` itself

## 3. Completion

- [x] 3.1 Run `mvn test` and restart the app
