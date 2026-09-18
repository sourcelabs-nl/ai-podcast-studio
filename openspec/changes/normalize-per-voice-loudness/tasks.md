## 1. Carry the voice through to concatenation

- [x] 1.1 Add an `AudioChunk` data class (bytes plus `voiceId`) and change `TtsResult.audioChunks` to `List<AudioChunk>`; verify by compiling, which surfaces every consumer that must change
- [x] 1.2 Populate the voice in `InworldTtsProvider`, keeping `ChunkWork.voiceId` instead of discarding it in `generateDialogue`, and verify the existing Inworld provider tests still pass
- [x] 1.3 Populate the voice in `ElevenLabsTtsProvider`, `ElevenLabsDialogueTtsProvider` and `OpenAiTtsProvider`, and verify their tests still pass
- [x] 1.4 Update the three `concatenate` callers (`TtsPipeline`, `PreviewAudioService`, `PreviewAudioStore`) and verify `mvn test` compiles and passes with concatenation still unnormalized

## 2. Loudness measurement and correction

- [x] 2.1 Add a helper that measures integrated loudness of an audio file with ffmpeg `ebur128` and parses the value, returning null on failure; verify with a unit test over a fixture of known level
- [x] 2.2 Add a helper that applies a gain and a peak limiter to a set of chunks, producing re-encoded files in the target format; verify with a unit test that the output level moved by the requested amount
- [x] 2.3 Implement the per-voice correction: group chunks by voice, measure each voice over its concatenated chunks, apply gain toward -16 LUFS with the limiter, measure again and apply the residual; verify with a unit test that two fixtures 5 LU apart end within 0.5 LU of the target and of each other

## 3. Concatenation

- [x] 3.1 Replace the `-c copy` concat with an encode to the probed target format, keeping `probeFormat` and its warning fallbacks as the source of that format; verify the existing format scenarios in `AudioConcatenatorTest` still pass
- [x] 3.2 Exclude the prepended silence from loudness measurement and verify a test asserting the measured level is unaffected by the silence
- [x] 3.3 Degrade to uncorrected concatenation with a warning when measurement or correction fails for a voice, and verify with a test that forces a measurement failure
- [x] 3.4 Log the gain applied per voice so an extreme correction is visible in `app.log`, and verify the line appears when generating

## 4. Retire the concatenation flag

- [x] 4.1 Remove `TtsResult.requiresConcatenation` and route every caller through the concatenator, including the single-chunk path that previously skipped levelling and the leading silence; verify `mvn test` passes
- [x] 4.2 Update the `tts-provider-abstraction`, `elevenlabs-tts` and `inworld-tts` spec deltas accordingly and verify `openspec validate` passes

## 5. Verification

- [x] 5.1 Extend `AudioConcatenatorTest` to cover the scenarios in the specs delta: two voices matched, dynamics within a voice preserved, single voice corrected, peak ceiling respected, measurement failure tolerated
- [x] 5.2 Run `mvn test` and confirm the whole suite passes
- [x] 5.3 Restart the app (`./stop.sh` then `./start.sh`) and generate a real dialogue episode; verify with `ffmpeg ebur128` that both voices land within 0.5 LU of -16 LUFS and the true peak stays at or below -1.0 dBFS
- [ ] 5.4 Listen to the generated episode and confirm the turn changes no longer carry a level jump
- [x] 5.5 Run `/code-review --all` and fix any violations, repeating until the review is clean
