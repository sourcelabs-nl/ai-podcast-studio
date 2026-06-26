## Why

The pipeline mixes coroutine `suspend` code with thread-blocking bridges. Four `runBlocking` calls (`ArticleScoreSummarizer.scoreSummarize`, `InworldTtsProvider`, `FtpPublisher.update`, `SourcePollingScheduler.catchUpPoll`) and several blocking leaf I/O calls (LLM composers, TTS HTTP clients, FTP/SoundCloud I/O) run on whatever dispatcher the caller happens to be on. On a coroutine root that is not `Dispatchers.IO` (e.g. a `suspend` Spring MVC handler), these block a dispatcher thread instead of suspending. Under load this starves the dispatcher and the JDBC connection pool, which we have already observed as Hikari "thread starvation" warnings. The goal is end-to-end non-blocking: `suspend` from each coroutine root down, with every blocking I/O call confined to `Dispatchers.IO`.

## What Changes

- Remove the four `runBlocking` bridges and propagate `suspend` up to the existing coroutine roots (suspend controllers, `scope.launch` background jobs, `@EventListener` loops). No new entry points; the roots are unchanged.
- Replace `runBlocking(Dispatchers.IO) { ... }` with `withContext(Dispatchers.IO) { ... }` in `suspend` functions.
- Make `TtsProvider.generate` and `EpisodePublisher.update` `suspend` (interface + all implementors).
- Wrap every remaining blocking leaf I/O call in `withContext(Dispatchers.IO)` so no blocking call runs on a non-IO dispatcher: LLM composers (`briefing`/`dialogue`/`interview`), `TopicDedupFilter`, the OpenAI/ElevenLabs/Inworld TTS clients, and the FTP/SoundCloud publishers.
- Delete `SourcePollingScheduler.catchUpPoll`; its only caller is already `suspend` and calls `pollPodcastSourcesNow()` directly.

No user-facing behavior changes: same endpoints, same events, same outputs. This is an internal concurrency-model change.

## Capabilities

### New Capabilities
- `non-blocking-execution`: Defines the concurrency contract for the pipeline — coroutine roots are `suspend`/launched, no `runBlocking` on request or background paths, and all blocking I/O is confined to `Dispatchers.IO` via `suspend`/`withContext`.

### Modified Capabilities
<!-- None: no spec-level behavior changes. This change alters the threading model only. -->

## Impact

- **LLM:** `ArticleScoreSummarizer`, `LlmPipeline` (`aggregateScoreAndFilter`, `compose`, plus already-suspend `run`/`preview`/`dedup`), `TopicDedupFilter`, the three composers.
- **TTS:** `TtsProvider` interface + `OpenAiTtsProvider`, `ElevenLabsTtsProvider`, `ElevenLabsDialogueTtsProvider`, `InworldTtsProvider`, `InworldApiClient`, `TtsPipeline`, `AudioGenerationService.doGenerateAudio`.
- **Publishing:** `EpisodePublisher` interface + `FtpPublisher`, `SoundCloudPublisher`, `PublishingService.updateExisting`.
- **Scheduling:** `SourcePollingScheduler.catchUpPoll` removed; `BriefingGenerationScheduler.ensureFreshPolling` calls the suspend poll directly.
- **Tests:** suspend signature changes ripple into existing unit tests; affected tests move to `runTest`. No production dependency changes.
- **No** API, schema, or config changes.
