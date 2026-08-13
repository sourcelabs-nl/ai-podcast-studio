## Why

Inworld's [Generating Naturally Sounding Speech](https://docs.inworld.ai/tts/best-practices/generating-speech) guide documents several quality levers we either use incorrectly or do not use at all. Two are outright bugs (we expose an invalid `deliveryMode` value and instruct the LLM to emit an unrecognised non-verbal tag), and the largest quality win, `synthesisContext`, is free for us: because we chunk scripts up front we already know each chunk's neighbours, so we can give the model conversational context without giving up any parallelism.

## What Changes

**Correctness fixes**

- Replace the invalid `deliveryMode` value `EXPRESSIVE` with `CREATIVE` everywhere (backend spec, settings UI, tests). The documented enum is `DELIVERY_MODE_UNSPECIFIED | STABLE | BALANCED | CREATIVE`. Existing podcasts with `deliveryMode: "EXPRESSIVE"` stored in `ttsSettings` SHALL be migrated to `CREATIVE`.
- Correct the non-verbal tag `[clear_throat]` to `[clear throat]` (with a space) in the script guidelines and the post-processor whitelist. Inworld treats an unrecognised sound name as a steering *instruction* rather than a sound, so the underscore form is silently wrong today.
- Change `DEFAULT_MODEL` from `inworld-tts-1.5-max` to `inworld-tts-2`, matching the model actually in use and the only model that supports steering.

**Conversation context (`synthesisContext`)**

- Send `synthesisContext.previousRequests` with each synthesis call, carrying the text of the preceding chunks (monologue) or preceding turns (dialogue/interview), capped to a bounded window. This improves intonation continuity across splice points and fixes flat delivery on short dialogue turns.

**Steering on `inworld-tts-2`**

- Extend the script guidelines so the composer may emit a single free-form English steering instruction (for example `[warm and conversational with an easy pace]`) at the start of a speaker turn or segment, following Inworld's rules: one instruction per input, placed at the start, English only, matching the content, `[reset]` to clear.
- Because an instruction stays in force until changed and our chunking splits a turn into multiple requests, the provider SHALL track the active instruction and re-emit it at the head of every subsequent chunk so later chunks do not silently lose the direction.
- Make the post-processor's handling of bracketed tags deliberate: today a single-word tag such as `[excited]` is stripped while a multi-word `[say excitedly]` passes through untouched. Steering tags SHALL be preserved on `inworld-tts-2` and stripped on models that do not support steering (which would otherwise read them aloud).

**Script and request polish**

- Add an acronym rule to the guidelines (expand on first use, then speak as a word if pronounceable or spell out if not). Inworld's normalization explicitly does not cover domain acronyms, which are pervasive in an AI-news podcast.
- Allow SSML break tags (`<break time="1s" />`) in the guidelines for deliberate beats between segments, documenting the API constraints (max 20 per request, max 10s each) and that a break at a chunk seam is redundant.
- Add CAPS emphasis (including syllable-level, "AbsoLUTEly") alongside `*word*` emphasis.
- Chunk at 1900 characters with paragraph-break-first priority (paragraph, then line, then sentence, then space), matching Inworld's reference chunker, so splices land at natural pauses and stay clear of the 2000-character hard limit.
- Clamp `speakingRate` to the documented `[0.5, 1.5]` range and warn when it is below the recommended `0.8`.
- Send the BCP-47 `language` field derived from the podcast's configured language, so a localized voice prompt is used instead of relying on auto-detection.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `inworld-tts`: default model, `deliveryMode` enum values, `synthesisContext`, steering support with per-chunk instruction re-emission, `language` field, `speakingRate` clamping, `maxChunkSize`, and the script guidelines (non-verbal tag spelling, acronyms, break tags, CAPS emphasis).
- `inworld-script-postprocessing`: non-verbal tag whitelist spelling, and model-aware retention of steering instruction tags.
- `tts-generation`: `TextChunker` gains paragraph-break-first splitting.
- `frontend-podcast-settings`: delivery mode options replace `EXPRESSIVE` with `CREATIVE`.

## Impact

- Backend: `InworldTtsProvider`, `InworldApiClient` (`InworldSynthesisOptions`, request body), `InworldScriptPostProcessor`, `TextChunker`.
- Frontend: `frontend/src/app/podcasts/[podcastId]/settings/page.tsx` delivery mode select and its description.
- Database: one-off migration rewriting `deliveryMode: "EXPRESSIVE"` to `"CREATIVE"` in stored podcast `ttsSettings`.
- Tests: `InworldTtsProviderTest`, `InworldApiClientTest`, `InworldScriptPostProcessorTest`, `TextChunker` tests, plus a live probe to confirm `CREATIVE` is accepted and to check whether `synthesisContext` text counts toward `usage.processedCharactersCount` (billing).
- Cost: `application.yaml` already prices `inworld-tts-2` at 25.00 per million characters versus 10.00 for `inworld-tts-1.5-max`. Making tts-2 the code default aligns the default with current usage; it does not change what already-configured podcasts pay.
- Depends on the unarchived change `tts-audio-format-and-inworld-enhanced-quality`; the delta specs here are written against that change's post-state.
