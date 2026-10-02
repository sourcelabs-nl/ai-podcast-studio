# Tasks

## 1. Composers

- [x] 1.1 Extract `TaggedScriptComposer` and make the dialogue and interview composers use it, and verify the existing composer tests pass unchanged apart from construction, plus a test that both composers produce the same result shape through it

## 2. SSE

- [x] 2.1 Add `SafeSseEmitter` and verify with a test that concurrent sends and a completion do not interleave
- [x] 2.2 Move the podcast preview stream into `PreviewStreamer`, use `SafeSseEmitter` in it and in the audio preview endpoint, and verify the existing preview tests pass with the same events and payloads

## 3. Publishing

- [x] 3.1 Split `PublishingService.publish` into named steps and verify every existing publishing test passes unchanged

## 4. Completion

- [x] 4.1 Run `mvn test` and restart the app
