## ADDED Requirements

### Requirement: Coroutine roots are non-blocking
The system SHALL drive all long-running pipeline work from coroutine roots that are either `suspend` entry points (Spring MVC handlers) or coroutines launched on a managed `CoroutineScope`. The system SHALL NOT use `runBlocking` on any request-handling or background-execution path.

#### Scenario: Suspend controller path has no blocking bridge
- **WHEN** a `suspend` controller handler (e.g. preview or publish) invokes the LLM, TTS, or publishing pipeline
- **THEN** the call chain reaches the underlying I/O without any `runBlocking`, suspending instead of blocking the request dispatcher

#### Scenario: Background job path has no blocking bridge
- **WHEN** a scheduler loop or `scope.launch` background job (polling, briefing generation, audio generation, auto-publish, retry) invokes the pipeline
- **THEN** the work runs through `suspend` functions with no `runBlocking` between the launch and the I/O

### Requirement: Blocking I/O is confined to Dispatchers.IO
The system SHALL execute every blocking I/O call (LLM `ChatClient` calls, TTS HTTP/synthesis calls, FTP transfers, and SoundCloud HTTP calls) within `Dispatchers.IO`, using `suspend` functions and `withContext(Dispatchers.IO)`. No blocking I/O call SHALL run on a non-IO dispatcher.

#### Scenario: LLM call runs on the IO dispatcher
- **WHEN** article scoring, topic dedup, composition, or recap generation invokes a blocking `ChatClient` call
- **THEN** the call executes inside `withContext(Dispatchers.IO)` (or a `Dispatchers.IO` scope), not on the caller's dispatcher

#### Scenario: TTS and publishing I/O runs on the IO dispatcher
- **WHEN** a TTS provider synthesizes audio, or a publisher uploads via FTP or SoundCloud HTTP
- **THEN** the network call executes within `Dispatchers.IO`

### Requirement: Transactional work does not cross dispatcher switches
The system SHALL NOT wrap a `withContext` dispatcher switch inside a `@Transactional suspend` method, because Spring binds the transaction to a thread. Blocking network/LLM calls confined to `Dispatchers.IO` SHALL contain no surrounding transactional database work.

#### Scenario: No transaction spans a dispatcher switch
- **WHEN** a service method is annotated `@Transactional`
- **THEN** it does not switch dispatchers via `withContext` within the transactional boundary, keeping the transaction on a single thread

### Requirement: Provider and publisher abstractions are suspend
The `TtsProvider.generate` and `EpisodePublisher.update` operations SHALL be `suspend` functions, and all implementors SHALL conform, so callers can invoke them without blocking bridges.

#### Scenario: All TTS providers expose a suspend generate
- **WHEN** any `TtsProvider` implementor (OpenAI, ElevenLabs, ElevenLabs dialogue, Inworld) is invoked
- **THEN** `generate` is a `suspend` function and performs its synthesis I/O on `Dispatchers.IO`

#### Scenario: All publishers expose a suspend update
- **WHEN** any `EpisodePublisher` implementor performs an update of an existing publication
- **THEN** `update` is a `suspend` function with no internal `runBlocking`
