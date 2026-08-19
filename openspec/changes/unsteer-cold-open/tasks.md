<!-- Implemented before this change was written; every task below is already done. -->

## 1. Post-processor

- [x] 1.1 Add `stripLeadingInstruction(text: String): String` to `InworldScriptPostProcessor`, removing a leading bracketed delivery instruction (and one following space) while keeping a leading sound tag
- [x] 1.2 Add a `LEADING_INSTRUCTION` regex anchored at the start of the text, and document on the function why the script opening needs this

## 2. Provider wiring

- [x] 2.1 Add an `isScriptOpening: Boolean` parameter to `InworldTtsProvider.prepareChunks` and document that it marks the request sent without `synthesisContext`
- [x] 2.2 Apply `stripLeadingInstruction` to chunk index `0` only, after `InworldSteering.reemitInstructions`, so later chunks keep the re-emitted direction
- [x] 2.3 Pass `isScriptOpening = true` from `generateMonologue`, and `isScriptOpening = index == 0` from `generateDialogue`

## 3. Composer guidance

- [x] 3.1 Add a `CORE_GUIDELINES` bullet forbidding a delivery direction on the script's very first turn, with the reason (no preceding audio to anchor the cue)

## 4. Tests

- [x] 4.1 `InworldScriptPostProcessorTest`: leading instruction removed, leading sound tag kept, non-leading instruction kept, untagged text unchanged
- [x] 4.2 `InworldTtsProviderTest`: monologue opening stripped, first dialogue turn stripped while the second keeps its instruction, leading sound tag preserved
- [x] 4.3 `InworldTtsProviderTest`: assert every `PodcastStyle` emits the first-turn ban in its guidelines
- [x] 4.4 Update `re-emits the steering instruction on later chunks of a monologue` to expect the opening chunk unsteered
- [x] 4.5 Update `steering instructions do not leak across dialogue turns` to set its cue on a middle turn, so it still exercises leakage now that turn 0 is stripped
- [x] 4.6 Run `mvn test` and confirm the full suite passes (1112 tests, 0 failures)

## 5. Deploy

- [x] 5.1 Restart the application (`./stop.sh` then `./start.sh`) so subsequent generations use the new pipeline
