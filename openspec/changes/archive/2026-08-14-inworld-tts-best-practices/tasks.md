## 1. Verify the Inworld API contract

- [x] 1.1 Confirm the documented `deliveryMode` enum (`DELIVERY_MODE_UNSPECIFIED | STABLE | BALANCED | CREATIVE`) and that `EXPRESSIVE` is not a member
- [x] 1.2 Confirm the documented sound names, including `[clear throat]` with a space
- [x] 1.3 Confirm the `synthesisContext.previousRequests` shape (a list of objects with a `text` field)
- [x] 1.4 Confirm the steering rules on `inworld-tts-2`: one instruction per input, placed at the start, English only, `[reset]` to clear
- [x] 1.5 Confirm the SSML break tag limits (max 20 per request, max 10s each) and syntax
- [x] 1.6 Confirm the `language` field accepts a BCP-47 tag and that bare ISO 639-1 codes are supported
- [x] 1.7 Confirm the `speakingRate` range `[0.5, 1.5]`
- [ ] 1.8 Live probe: confirm the API accepts `deliveryMode: "CREATIVE"` — DEFERRED, no live Inworld calls were made for this change
- [ ] 1.9 Live probe: check whether `synthesisContext` text counts toward `usage.processedCharactersCount` (billing) — DEFERRED, no live Inworld calls were made for this change

## 2. Correctness fixes

- [x] 2.1 Change `InworldTtsProvider.DEFAULT_MODEL` to `inworld-tts-2`
- [x] 2.2 Replace `EXPRESSIVE` with `CREATIVE` in the settings UI select and its description
- [x] 2.3 Correct `[clear_throat]` to `[clear throat]` in the script guidelines
- [x] 2.4 Correct the post-processor sound whitelist to `clear throat`, rewriting a legacy `[clear_throat]` to the documented spelling
- [x] 2.5 Add Flyway migration `V65__rewrite_expressive_delivery_mode.sql` rewriting stored `"deliveryMode": "EXPRESSIVE"` to `"CREATIVE"`

## 3. Request body: language, speaking rate, synthesis context

- [x] 3.1 Extend `InworldSynthesisOptions` with `language` and `previousRequests`
- [x] 3.2 Emit the top-level `language` field when set and non-blank
- [x] 3.3 Emit `synthesisContext.previousRequests` as `[{"text": ...}]`, omitting the field when the window is empty
- [x] 3.4 Clamp `audioConfig.speakingRate` to `[0.5, 1.5]` in `buildSynthesisBody`, warning on clamp and on values below the recommended `0.8`
- [x] 3.5 Build the bounded context window in `InworldTtsProvider` (max 3 texts, max 2000 characters, counted backwards) for both monologue chunks and flattened dialogue chunks

## 4. Steering

- [x] 4.1 Add `InworldSteering` with `supportsSteering(modelId)` and `reemitInstructions(chunks)`
- [x] 4.2 Track the active instruction across a turn's chunks, treating `[reset]` as a clear and sound tags as neutral
- [x] 4.3 Prepend the active instruction to any chunk that does not already open with one
- [x] 4.4 Reset tracking per dialogue turn so directions do not leak between speakers
- [x] 4.5 Apply re-emission only when the resolved model supports steering

## 5. Post-processor

- [x] 5.1 Widen the bracketed-tag pattern to multi-word tags and add a `retainSteeringInstructions` parameter
- [x] 5.2 Keep sound names always, keep alphabetic instruction tags only when steering is retained, strip everything else
- [x] 5.3 Pass `retainSteeringInstructions` from `InworldTtsProvider` based on the resolved model

## 6. Chunking

- [x] 6.1 Rewrite `TextChunker` to try paragraph, line, sentence, then space separators in priority order, with a hard cut as a last resort
- [x] 6.2 Keep separators attached to the preceding text so paragraph structure survives
- [x] 6.3 Set `InworldTtsProvider.maxChunkSize` to 1900

## 7. Script guidelines

- [x] 7.1 Add the acronym rule (expand on first use; speak as a word if pronounceable, spell out if not)
- [x] 7.2 Add SSML break tags with their limits and the note about redundancy at a paragraph break
- [x] 7.3 Add CAPS emphasis (whole word and syllable level) alongside `*word*`
- [x] 7.4 Add the steering instruction rule (one per turn, at the start, English, `[reset]` to clear)

## 8. Tests

- [x] 8.1 `InworldApiClientTest`: `language` present/absent, `synthesisContext` shape and omission, `speakingRate` clamping above/below range and below the recommended minimum, `CREATIVE` delivery mode
- [x] 8.2 `InworldTtsProviderTest`: new default model, context window for monologue and dialogue, steering retention per model, guideline content
- [x] 8.3 `InworldSteeringTest`: re-emission, replacement, `[reset]`, chunk already opening with an instruction, sound tags neutral
- [x] 8.4 `InworldScriptPostProcessorTest`: `[clear throat]`, legacy `[clear_throat]`, steering retained/stripped, non-alphabetic tags
- [x] 8.5 `TextChunkerTest`: paragraph-first splitting, separator retention, unbreakable run, 1900 limit
- [x] 8.6 Run the full `mvn test` suite and confirm it is green

## 9. Verification

- [x] 9.1 Typecheck the frontend with `npx tsc --noEmit`
- [x] 9.2 Review the diff against the architecture guidelines
