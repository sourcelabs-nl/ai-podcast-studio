# Tasks

## 1. Contract

- [x] 1.1 Add the four API exceptions and `ApiExceptionHandler`, and verify with a MockMvc test per exception type asserting status and body `{"error": message}`

## 2. Services throw typed exceptions

- [x] 2.1 `PodcastImageService` throws `InvalidRequestException` and `SourceService` throws `UnprocessableRequestException` for the existing validation failures, with unchanged messages, and verify with service tests asserting type and text
- [x] 2.2 `PublishingService.rebuildSoundCloudPlaylist` throws `InvalidRequestException` for its preconditions and `OperationFailedException` for failures, and verify with service tests
- [x] 2.3 `DatabaseBackupService.backup` and the recap regeneration path throw `OperationFailedException("Backup failed: ...")` and `("Failed to regenerate recap: ...")` with the cause, and verify with service tests
- [x] 2.4 Add `VoiceDiscoveryService` wrapping the ElevenLabs and Inworld clients: `UpstreamServiceException` for upstream failures, `InvalidRequestException` for the not-configured case and for an unsupported provider, and verify with service tests
- [x] 2.5 Make the SoundCloud track deletion and the preview sample synthesis throw `OperationFailedException` with their current messages, and verify with service tests
- [x] 2.6 Add `ResearchService.testTavily` and slim `ResearchTestController`, and verify with service tests for the missing key, success and failure results

## 3. Controllers

- [x] 3.1 Remove the inline try/catch from `PlaylistController`, `PodcastImageController`, `SourceController`, `VoiceController`, `BackupController`, `PreviewAudioController.sample`, `EpisodeController.regenerateRecap`, `SoundCloudOAuthController.deleteTrack`, and verify every existing controller test still sees the same status and body

## 4. Completion

- [x] 4.1 Run `mvn test` and restart the app
