## 1. LLM group

- [x] 1.1 Make `ArticleScoreSummarizer.scoreSummarize` `suspend`; replace `runBlocking(Dispatchers.IO)` with `withContext(Dispatchers.IO)` around the `supervisorScope`/`async` block
- [x] 1.2 Wrap the blocking `chatClient...call()` in `TopicDedupFilter.filter` in `withContext(Dispatchers.IO)`
- [x] 1.3 Make both `compose` overloads `suspend` in `BriefingComposer`, `DialogueComposer`, `InterviewComposer`; wrap each `chatClient...call()` in `withContext(Dispatchers.IO)`
- [x] 1.4 Make `EpisodeRecapGenerator` generation `suspend`; wrap its `chatClient...call()` in `withContext(Dispatchers.IO)`
- [x] 1.5 Make `LlmPipeline.aggregateScoreAndFilter`, `compose`, `recompose`, and `scoreReadySources` `suspend` to match the now-suspend callees
- [x] 1.6 Propagate `suspend` through `PodcastService` (`scoreReadySources`, `recompose`/`regenerateEpisode` paths) up to existing suspend roots; do not introduce any new `runBlocking`
- [x] 1.7 Verify no `@Transactional` method now wraps a `withContext` dispatcher switch (Decision 3)
- [x] 1.8 Update affected unit tests to `runTest`; `mvn test` green for the LLM group

## 2. TTS group

- [x] 2.1 Make `TtsProvider.generate` `suspend` in the interface
- [x] 2.2 Update `OpenAiTtsProvider.generate` to `suspend`; wrap `speechModel.call(...)` in `withContext(Dispatchers.IO)`
- [x] 2.3 Update `ElevenLabsTtsProvider.generate` and `ElevenLabsDialogueTtsProvider.generate` to `suspend`; wrap `apiClient.textToSpeech`/`textToDialogue` in `withContext(Dispatchers.IO)`
- [x] 2.4 Update `InworldTtsProvider.generate` to `suspend`; replace both `runBlocking(Dispatchers.IO) { ...async... }` with `withContext(Dispatchers.IO) { coroutineScope { ...async... } }`
- [x] 2.5 Make `TtsPipeline.generate`/`generateForExistingEpisode`/`callProvider` `suspend`; make `AudioGenerationService.doGenerateAudio` `suspend` (root remains the existing `scope.launch`)
- [x] 2.6 Update affected TTS tests to `runTest`; `mvn test` green for the TTS group

## 3. Publishing group

- [x] 3.1 Make `EpisodePublisher.update` `suspend` in the interface (default impl)
- [x] 3.2 Make `FtpPublisher.update` `suspend` and drop its `runBlocking { publish(...) }`; wrap the FTP connect/upload/disconnect block in `FtpPublisher.publish` in `withContext(Dispatchers.IO)`
- [x] 3.3 Make `SoundCloudPublisher.update` `suspend`; wrap its blocking SoundCloud HTTP calls in `withContext(Dispatchers.IO)`
- [x] 3.4 Make `PublishingService.updateExisting` `suspend` (caller `publish` is already suspend)
- [x] 3.5 Update affected publishing tests to `runTest`; `mvn test` green for the publishing group

## 4. Scheduler

- [x] 4.1 Delete `SourcePollingScheduler.catchUpPoll`; change `BriefingGenerationScheduler.ensureFreshPolling` to call `pollPodcastSourcesNow()` directly (make it `suspend` if needed; its containing path is already suspend)
- [x] 4.2 `mvn test` green

## 5. Verification

- [x] 5.1 Full `mvn test` green
- [x] 5.2 Run `/code-review --all`; fix violations and re-run until clean
- [x] 5.3 `./stop.sh && ./start.sh`; exercise generate + auto-publish via the API and confirm no `runBlocking` remains in `src/main/kotlin` (`grep -rn runBlocking`)
