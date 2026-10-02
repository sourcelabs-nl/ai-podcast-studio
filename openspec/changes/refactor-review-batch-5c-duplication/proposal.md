# Proposal

## Why

The code review of 2026-10-01 found three kinds of duplication and size. `DialogueComposer` and `InterviewComposer` carry the same compose skeleton line for line. The two SSE endpoints that stream previews have drifted: only one serializes its sends, although `SseEmitter` is not safe for concurrent sends and the other sends from a heartbeat and a progress callback at once. And `PublishingService.publish` is about 115 lines that mix validation, same-day cleanup, claiming, uploading and side effects.

## What Changes

- The compose skeleton of the dialogue and interview composers moves into one `TaggedScriptComposer`; each composer keeps its own prompt and its own roles.
- One `SafeSseEmitter` serializes sends and completion for both preview endpoints, and the podcast preview stream moves out of `PodcastController` into a `PreviewStreamer` component.
- `PublishingService.publish` is split into named steps without changing its order or its outcomes.

Prompts, responses and every observable result stay identical.

## Capabilities

### New Capabilities

### Modified Capabilities

## Impact

- `llm/DialogueComposer.kt`, `llm/InterviewComposer.kt`, new `llm/TaggedScriptComposer.kt`, and the tests that construct the composers.
- New `podcast/SafeSseEmitter.kt` and `podcast/PreviewStreamer.kt`, `podcast/PodcastController.kt`, `tts/PreviewAudioController.kt`.
- `publishing/PublishingService.kt`.
