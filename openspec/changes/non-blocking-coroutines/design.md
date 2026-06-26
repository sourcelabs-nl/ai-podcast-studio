## Context

The app already runs its long-running work on coroutines, but mixes `suspend` code with thread-blocking bridges. There are six `CoroutineScope(Dispatchers.IO + SupervisorJob())` roots (two scheduler loops, audio generation, auto-publish, preview SSE, retry), all cancelled in `@PreDestroy`, and each `launch` body has its own `try/catch`. The problem is below those roots:

- **Four `runBlocking` bridges:** `ArticleScoreSummarizer.scoreSummarize` (`runBlocking(Dispatchers.IO)`), `InworldTtsProvider` monologue/dialogue (`runBlocking(Dispatchers.IO)`), `FtpPublisher.update` (`runBlocking`), `SourcePollingScheduler.catchUpPoll` (`runBlocking`).
- **Blocking leaf I/O on the caller's dispatcher:** Spring AI `ChatClient.call()` in the three composers, `TopicDedupFilter`, and `EpisodeRecapGenerator`; the OpenAI/ElevenLabs/Inworld TTS HTTP clients; the FTP and SoundCloud network I/O.

When a coroutine root is `Dispatchers.IO` these blocking calls are harmless. When it is a `suspend` Spring MVC handler (preview, publish) they block the MVC coroutine dispatcher, and across the app they have already produced Hikari "thread starvation" warnings.

## Goals / Non-Goals

**Goals:**
- Every coroutine root is `suspend` (controllers) or a managed `launch` (background); no `runBlocking` on request or background paths.
- Every blocking I/O call (LLM, TTS, FTP, HTTP) is confined to `Dispatchers.IO` via `suspend` + `withContext(Dispatchers.IO)`.
- No behavior change: same endpoints, events, and outputs.

**Non-Goals:**
- Replacing blocking clients (Spring AI `ChatClient`, Apache Commons Net FTP, SoundCloud HTTP) with reactive/non-blocking clients. We confine them to `Dispatchers.IO`, we do not rewrite them.
- Changing the launch/exception/lifecycle model (already sound). The two minor launch gaps found during audit are explicitly out of scope for this change.
- Making `@Scheduled` suspend — there are no `@Scheduled` methods; schedulers are `@EventListener` + `launch` loops.

## Decisions

**1. Bridge removal by propagating `suspend` to the nearest existing root (not by introducing new `runBlocking`).**
- `scoreSummarize` → `suspend`, body `withContext(Dispatchers.IO) { supervisorScope { ... } }`. Forces `LlmPipeline.aggregateScoreAndFilter` and `compose` to `suspend`; their callers (`generateBriefing`, `doRetry`, `previewBriefing`, `recompose`, `scoreReadySources`) become/are already `suspend` up to the suspend controllers and launch loops.
- `TtsProvider.generate` → `suspend` (interface + 4 impls). `InworldTtsProvider` replaces `runBlocking(Dispatchers.IO) { ...async... }` with `withContext(Dispatchers.IO) { coroutineScope { ...async... } }`. `TtsPipeline.generate`/`generateForExistingEpisode`/`callProvider` and `AudioGenerationService.doGenerateAudio` → `suspend` (root is the existing `scope.launch`).
- `EpisodePublisher.update` → `suspend` (interface default + `FtpPublisher` + `SoundCloudPublisher`). `PublishingService.updateExisting` → `suspend`; drop `FtpPublisher`'s `runBlocking`.
- Delete `SourcePollingScheduler.catchUpPoll`; `BriefingGenerationScheduler.ensureFreshPolling` calls `pollPodcastSourcesNow()` directly (its containing path is already `suspend`).

*Alternative considered:* keep `runBlocking` but always on `Dispatchers.IO`. Rejected — it still blocks a thread and defeats the goal; the roots are already coroutine-capable so `suspend` is free.

**2. Wrap only the blocking call, at the narrowest scope.**
Wrap the actual network/LLM call (`chatClient...call()`, `apiClient.textToSpeech/...`, `speechModel.call(...)`, FTP connect/upload, SoundCloud HTTP) in `withContext(Dispatchers.IO) { ... }` — not the surrounding pure-CPU prompt building or result mapping. Keeps the IO dispatcher doing only IO.

**3. Keep transactional/DB work out of dispatcher switches.** *(key correctness decision)*
Spring's `@Transactional` binds the transaction to a thread via `ThreadLocal`; a `suspend` function that switches dispatchers mid-transaction breaks that binding. Mitigation by construction: the calls we wrap in `withContext(Dispatchers.IO)` are pure network/LLM calls with no surrounding transaction. DB writes (e.g. `articleRepository.save` in `scoreSummarize`) are single autocommit saves (Spring Data JDBC + SQLite), not multi-statement transactions, and stay on whatever context invokes them. No method that is both `@Transactional` and wraps a `withContext` dispatcher switch will be introduced. Each `runTest`/review pass verifies no `@Transactional suspend` method spans a dispatcher switch.

**4. Test migration to `runTest`.** Suspend signature changes ripple into unit tests that call these methods directly (`LlmPipelineTest`, composer tests, TTS/publishing tests). Affected tests move to `kotlinx-coroutines-test` `runTest { }` (the pattern already used in `LlmPipelineTest` after the earlier refactor).

## Risks / Trade-offs

- **`@Transactional` + dispatcher switch breaks the transaction** → Decision 3: wrap only non-transactional network calls; never put `withContext` inside a `@Transactional suspend` method. Verified in review.
- **Wide diff touching LLM, TTS, publishing at once** → Land in the task order LLM → TTS → publishing → scheduler, running `mvn test` after each group so a break is localized.
- **Spring AI `ChatClient` and FTP remain blocking clients** → Acceptable; confined to `Dispatchers.IO`. Non-goal to replace them.
- **`suspend` interface methods change all implementors** → `TtsProvider` (4) and `EpisodePublisher` (2) implementors must all be updated in lock-step or the build breaks; covered as single tasks per interface.
- **Behavior regressions are silent** (no API change) → Mitigate with full `mvn test`, `/code-review --all` loop, then `./stop.sh && ./start.sh` and exercise generate + publish via the API.

## Migration Plan

1. LLM group: `scoreSummarize`, composers, `TopicDedupFilter`, `EpisodeRecapGenerator`, `LlmPipeline.aggregateScoreAndFilter`/`compose`/`recompose`/`scoreReadySources`; `mvn test`.
2. TTS group: `TtsProvider` + 4 impls, `InworldApiClient`, `TtsPipeline`, `AudioGenerationService.doGenerateAudio`; `mvn test`.
3. Publishing group: `EpisodePublisher` + `FtpPublisher`/`SoundCloudPublisher`, `PublishingService.updateExisting`; `mvn test`.
4. Scheduler: delete `catchUpPoll`, inline suspend poll; `mvn test`.
5. `/code-review --all` loop until clean; restart app; smoke-test generate + auto-publish.

Rollback: pure refactor on a branch; revert the branch. No schema/config/data migration, so rollback is a code revert only.

## Open Questions

- None blocking. `EpisodeRecapGenerator` is included as leaf I/O for completeness; if its only caller is already on `Dispatchers.IO`, the wrap is defensive but harmless.
