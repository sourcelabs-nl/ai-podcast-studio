## ADDED Requirements

### Requirement: Preview audio sample
The system SHALL provide `POST /users/{userId}/podcasts/{podcastId}/preview/audio/sample`, accepting a previewed script in the request body as `{scriptText}` and returning the synthesised opening slice as `audio/mpeg` bytes in the response.

The slice SHALL be selected from the start of the script using the resolved provider's `maxChunkSize` and the shared text chunker, and SHALL end on a natural boundary rather than mid-sentence. For `DIALOGUE` and `INTERVIEW` styles the slice SHALL consist of whole speaker turns and SHALL cover at least two distinct speakers, extending contiguously to the second speaker's first turn when one chunk does not reach one. For all other styles the slice SHALL be the opening chunk.

The slice SHALL be synthesised through the same provider path an episode uses, so delivery mode, steering instructions, synthesis context, pronunciations, and post-processing all apply.

The request SHALL be answered synchronously, because one chunk of audio returns in seconds. Nothing SHALL be persisted: no episode is created, no audio is attached to any episode, and no cost is recorded.

#### Scenario: Monologue sample is the opening chunk
- **WHEN** a sample is requested for a podcast whose style is not dialogue or interview
- **THEN** the synthesised text is the opening chunk of the script and no more

#### Scenario: Monologue sample ends on a sentence boundary
- **WHEN** a monologue script is longer than one chunk and contains no paragraph breaks
- **THEN** the selected slice ends at a sentence boundary rather than mid-sentence

#### Scenario: Dialogue sample auditions two voices
- **WHEN** a sample is requested for a dialogue or interview podcast
- **THEN** the selected slice contains turns from at least two distinct speaker roles

#### Scenario: Dialogue sample never cuts a speaker turn
- **WHEN** the slice is selected from a tagged dialogue script
- **THEN** every turn it contains appears in the original script in full

#### Scenario: A long opening turn is extended past one chunk
- **WHEN** the first speaker's opening turn alone exceeds `maxChunkSize`
- **THEN** the slice still extends to the next speaker's first turn, even though it exceeds one chunk

#### Scenario: Dialogue script with a single speaker
- **WHEN** the script contains turns from only one role
- **THEN** the slice is what there is, with that single role

#### Scenario: Dialogue script without speaker tags
- **WHEN** a dialogue-style script contains no speaker tags
- **THEN** the slice falls back to the opening chunk of the raw text

#### Scenario: Blank script
- **WHEN** the request body carries a blank `scriptText`
- **THEN** the endpoint responds 400

#### Scenario: Podcast not owned by the caller
- **WHEN** the podcast in the path belongs to a different user
- **THEN** the endpoint responds 404

### Requirement: Preview audio cost estimate
The system SHALL provide `POST /users/{userId}/podcasts/{podcastId}/preview/audio/estimate`, accepting `{scriptText}` and returning `{characters, costCents}` for a full synthesis of that script.

The character count SHALL exclude speaker tags, which route a turn to a voice and are never sent to the provider. The cost SHALL be computed with `CostEstimator.estimateTtsCostCents` for the podcast's TTS provider and its configured model, falling back to the provider's first configured rate when the podcast names no model. When no rate is configured for the provider, `costCents` SHALL be null.

#### Scenario: Monologue estimate bills every character
- **WHEN** an estimate is requested for a monologue script
- **THEN** `characters` is the length of the sanitised script and `costCents` is derived from the configured per-million-character rate

#### Scenario: Dialogue estimate excludes speaker tags
- **WHEN** an estimate is requested for a tagged dialogue script
- **THEN** `characters` counts only the spoken text inside the tags

#### Scenario: No configured rate
- **WHEN** the podcast's TTS provider has no configured cost entry
- **THEN** `characters` is still reported and `costCents` is null

### Requirement: Full preview audio generation over SSE
The system SHALL provide `POST /users/{userId}/podcasts/{podcastId}/preview/audio`, accepting `{scriptText}` and returning `text/event-stream`.

The stream SHALL emit `progress` events shaped `{stage: "synthesizing", chunk, total}` as chunks complete, then a `result` event carrying an opaque `audioId`, then a `complete` event. On failure it SHALL emit an `error` event carrying a message. It SHALL emit a `heartbeat` event every 15 seconds so intermediaries do not time the stream out.

Providers may synthesise chunks concurrently, so writes to the stream SHALL be serialised: `SseEmitter` is not safe for concurrent sends and interleaved writes would corrupt the stream.

The endpoint SHALL NOT be a plain synchronous request: a real script is dozens of chunks and would exceed the Spring MVC async-request timeout.

No episode SHALL be created, no audio SHALL be attached to any episode, and the TTS cost SHALL NOT be persisted.

#### Scenario: Progress reported per chunk
- **WHEN** the provider completes a chunk during a full preview run
- **THEN** a `progress` event is emitted carrying the number completed and the total

#### Scenario: Completion carries an audio id
- **WHEN** the full run finishes
- **THEN** a `result` event carries an `audioId` and is followed by a `complete` event

#### Scenario: Failure is reported on the stream
- **WHEN** synthesis fails part-way through
- **THEN** an `error` event carrying the failure message is emitted and the stream is completed

#### Scenario: A dropped progress event does not abort the run
- **WHEN** sending a `progress` event fails because the client has gone away
- **THEN** the synthesis run continues rather than aborting

#### Scenario: Blank script
- **WHEN** the request body carries a blank `scriptText`
- **THEN** the endpoint responds 400

### Requirement: Preview audio retrieval
The system SHALL provide `GET /users/{userId}/podcasts/{podcastId}/preview/audio/{audioId}`, streaming the finished file as `audio/mpeg` with `Accept-Ranges: bytes`.

The `audioId` SHALL be an opaque, unguessable UUID. The endpoint SHALL verify that the caller owns the podcast in the path, and the file SHALL be resolved only within that podcast's own directory, so one user cannot read another user's preview audio. An identifier that is not a well-formed UUID SHALL be refused before it reaches the filesystem.

#### Scenario: Owner retrieves the audio
- **WHEN** the owning user requests a stored `audioId` for their podcast
- **THEN** the file is streamed as `audio/mpeg`

#### Scenario: Another podcast cannot reach the file
- **WHEN** the same `audioId` is requested under a different podcast
- **THEN** the endpoint responds 404

#### Scenario: Podcast not owned by the caller
- **WHEN** the podcast in the path belongs to a different user
- **THEN** the endpoint responds 404

#### Scenario: Malformed audio id
- **WHEN** the `audioId` is not a well-formed UUID (for example a traversal attempt)
- **THEN** it is refused without touching the filesystem and the endpoint responds 404

#### Scenario: Unknown audio id
- **WHEN** a well-formed `audioId` has no stored file
- **THEN** the endpoint responds 404

### Requirement: Preview audio storage and retention
Preview audio SHALL be written under `app.preview-audio.directory`, in a per-podcast subdirectory, named by its generated UUID. The location, the retention window (`app.preview-audio.retention-minutes`), and the sweep schedule (`app.preview-audio.sweep-cron`) SHALL be configurable, and the YAML values SHALL match the `PreviewAudioProperties` defaults.

Because preview audio belongs to no episode, no episode lifecycle deletes it. A scheduled sweep SHALL delete files older than the retention window and remove podcast directories it empties. The sweep SHALL run through the service layer on the existing Spring task scheduler and SHALL NOT create a thread pool. A failing sweep SHALL be logged and SHALL NOT escape the scheduled task.

#### Scenario: Expired audio is swept
- **WHEN** the sweep runs and a preview file is older than the retention window
- **THEN** the file is deleted and counted

#### Scenario: Fresh audio survives the sweep
- **WHEN** the sweep runs and a preview file is inside the retention window
- **THEN** the file is left in place

#### Scenario: Emptied directories are removed
- **WHEN** the sweep deletes the last file in a podcast's directory
- **THEN** that directory is removed

#### Scenario: Sweep on an empty store
- **WHEN** the sweep runs before any preview audio has been written
- **THEN** it completes without error and reports nothing deleted

#### Scenario: Failing sweep is contained
- **WHEN** the sweep throws
- **THEN** the failure is logged and does not propagate out of the scheduled task
