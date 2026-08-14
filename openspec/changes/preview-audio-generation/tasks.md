## 1. Sample slice selection

- [x] 1.1 Add `PreviewSampleSelector` returning a `PreviewSample` (text plus the roles it covers)
- [x] 1.2 Take the opening chunk for monologue styles, using the resolved provider's `maxChunkSize` and `TextChunker`
- [x] 1.3 Accumulate whole speaker turns for dialogue and interview styles so a turn is never cut
- [x] 1.4 Extend contiguously to the second speaker's first turn when one chunk does not reach two voices
- [x] 1.5 Fall back to the opening chunk when a dialogue script carries no speaker tags

## 2. Preview audio service and store

- [x] 2.1 Add `PreviewAudioStore` writing `{directory}/{podcastId}/{audioId}.mp3` under a random UUID
- [x] 2.2 Refuse a malformed audio id and any path that escapes the configured root
- [x] 2.3 Add the TTL sweep that deletes expired files and removes emptied podcast directories
- [x] 2.4 Add `PreviewAudioService` with sample synthesis, full generation, estimate, lookup, and sweep
- [x] 2.5 Route both sample and full synthesis through `TtsProviderFactory.resolve(podcast)`
- [x] 2.6 Count only spoken characters in the estimate, excluding speaker tags
- [x] 2.7 Add `PreviewAudioScheduler` running the sweep on the configured cron

## 3. Progress reporting

- [x] 3.1 Add `TtsProgressListener` and an optional `progress` field on `TtsRequest`
- [x] 3.2 Report completions from the Inworld provider, counting completions rather than indices
- [x] 3.3 Report completions from the OpenAI and both ElevenLabs providers
- [x] 3.4 Serialise writes to a preview audio SSE stream, which providers can drive concurrently
- [x] 3.5 Build the provider request from one shared factory so preview and episode stay identical

## 4. Endpoints

- [x] 4.1 `POST /preview/audio/sample` returning `audio/mpeg` bytes
- [x] 4.2 `POST /preview/audio/estimate` returning `{characters, costCents}`
- [x] 4.3 `POST /preview/audio` streaming `progress`, `result`, `complete`, and `error` events with heartbeats
- [x] 4.4 `GET /preview/audio/{audioId}` streaming the finished file as `audio/mpeg`
- [x] 4.5 Verify user and podcast ownership on every endpoint

## 5. Configuration

- [x] 5.1 Add `PreviewAudioProperties` (directory, retention minutes, sweep cron) to `AppProperties`
- [x] 5.2 Add `app.preview-audio` to `application.yaml` and the test configuration, in sync with the defaults

## 6. Deep dive filler words

- [x] 6.1 Route `PodcastStyle.DEEP_DIVE` to the casual guidance in `InworldTtsProvider.scriptGuidelines`
- [x] 6.2 Cover it with a test alongside the existing interview test

## 7. Frontend

- [x] 7.1 Add the SSE proxy route for the full-audio stream, dynamic and unbuffered
- [x] 7.2 Add a "Play Sample" button that plays the returned audio from a blob URL
- [x] 7.3 Add a "Generate Full Audio" button that estimates first and confirms the cost
- [x] 7.4 Show per-chunk progress and play the finished audio from the GET endpoint
- [x] 7.5 Reuse the SSE reading pattern, including the event name hoisted out of the read loop

## 8. Specs

- [x] 8.1 Add the `preview-audio` capability
- [x] 8.2 Record the Script tab's audio actions in `frontend-upcoming-episode`
- [x] 8.3 Record the deep dive guidance in `inworld-tts`
- [x] 8.4 Record the progress listener in `tts-provider-abstraction`

## 9. Verification

- [x] 9.1 `mvn test` green (1102 tests)
- [x] 9.2 `npx tsc --noEmit` clean
- [x] 9.3 `openspec validate --changes --strict` passes
- [ ] 9.4 Play a sample from the Script tab on a dialogue podcast and confirm both voices are audible
- [ ] 9.5 Generate full audio from the Script tab and confirm the progress count advances and the audio plays
- [ ] 9.6 Confirm a swept file is gone after the retention window
