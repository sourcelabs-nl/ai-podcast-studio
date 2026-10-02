# Tasks

## 1. Publishing

- [x] 1.1 In `PublishingService.publish`, keep the previous publication record when its unpublish fails (record `errorMessage`, no delete), and verify with a unit test that the record survives and the new episode is still published
- [x] 1.2 In `PublishingService.unpublish`, keep the record PUBLISHED with its `externalId` when removal fails, save the failure in `errorMessage`, rethrow, and verify with a unit test that no `episode.unpublished` event is published
- [x] 1.3 Guard `postPublish` and `export` after a successful unpublish and verify with a unit test that their failure still returns the UNPUBLISHED record and publishes the event
- [x] 1.4 Rethrow `CancellationException` in `PublishingService` where it catches `Exception` and verify with a test

## 2. Cancellation handling

- [x] 2.1 Add a `CancellationException` rethrow ahead of each generic catch in `AudioGenerationService`, `ArticleScoreSummarizer`, `AutoPublishListener` and `EpisodeService.generateAndStoreRecap`, and verify each with a test that a cancelled call leaves state unchanged (for audio generation: the episode is not saved `FAILED`)
- [x] 2.2 Do the same in `EpisodeController`, `PreviewAudioController` and `PodcastController` suspend and SSE code, and verify the handlers compile and the existing controller tests pass
- [x] 2.3 Do the same in `BriefingGenerationScheduler` and `SourcePollingScheduler`, and verify with `mvn test`

## 3. OAuth callbacks

- [x] 3.1 Escape the message in the `htmlPage` helpers of `SoundCloudOAuthController` and `XOAuthController` and verify with a test that an `error` parameter containing `<script>` is rendered escaped

## 4. LLM cache and advisor

- [x] 4.1 In `CachingChatModel`, store the response to a correction retry under the original prompt's key, replacing the rejected row, and verify with a test that the row keeps its id and key and holds the corrected response, while the first response and a tool-loop final response are still stored
- [x] 4.2 Move the `hasSpeakerTag` KDoc back above that function in `RoleTagValidationAdvisor` and verify the KDoc of `findMissingSpeakersProblem` still reads correctly

## 5. Completion

- [x] 5.1 Run `mvn test`, restart the app with `./stop.sh` and `./start.sh`, and re-run `/code-review --all` until the batch's findings are gone
