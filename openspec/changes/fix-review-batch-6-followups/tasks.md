# Tasks

## 1. Direct fixes

- [x] 1.1 Make the SoundCloud replacement keep its new id when the permalink claim fails, and verify with a publisher test
- [x] 1.2 Run `ScriptJudge` attempts on `Dispatchers.IO` with cancellation rethrown, cancel the `AutoPublishListener` scope on shutdown and drop the `@Transactional` on `regenerateSourcesHtml`, and verify with `mvn test`

## 2. Error contract

- [x] 2.1 Replace the hand-built 400 responses in `EpisodeController`, `PodcastPublicationsController`, `PreviewAudioController`, `ScriptMetricsController`, `EpisodeScoringController`, `BackupController`, `PublishingTestController` and `SoundCloudOAuthController` with `InvalidRequestException`, share the page and pageSize check, and verify every existing controller test sees the same status and body

## 3. Completion

- [x] 3.1 Run `mvn test` and restart the app
