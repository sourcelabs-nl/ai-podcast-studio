> Retroactive change: the implementation shipped in commits `12a0c1a` and `fcdfeb1` on
> branch `fix/tts-audio-format-and-inworld-enhanced-quality`. Tasks are recorded as done.

## 1. Verify the Inworld API contract

- [x] 1.1 Confirm against the Inworld API reference that the `audioConfig` bitrate field is `bitRate`, not `bitRateHertz`, and that `enhanceGeneration` is a top-level boolean defaulting to `false`
- [x] 1.2 Probe the live API to confirm `bitRate` is expressed in bits per second and is honoured (send 64000, verify 64000 out)
- [x] 1.3 Probe the live API to confirm `enhanceGeneration: true` leaves sample rate, channel count and bit rate unchanged, so stream-copy concatenation stays valid

## 2. Inworld enhanced audio quality and bitrate fix

- [x] 2.1 Add optional `enhanceGeneration: Boolean?` to `InworldSynthesisOptions`
- [x] 2.2 Extract request body construction into `InworldApiClient.buildSynthesisBody` so the wire format is directly assertable
- [x] 2.3 Emit `enhanceGeneration` in the request body only when it is set
- [x] 2.4 Rename `audioConfig.bitRateHertz` to `audioConfig.bitRate`
- [x] 2.5 Read `ttsSettings["enhanceGeneration"]` in `InworldTtsProvider` via `toBooleanStrictOrNull`, treating unparseable values as unset

## 3. Concatenation format matching

- [x] 3.1 Add an `AudioFormat` type carrying sample rate, channel count and bit rate, with an ffmpeg channel-layout accessor
- [x] 3.2 Add `AudioConcatenator.probeFormat` reading the format via `ffprobe`, returning null on non-zero exit, unparseable output, or a non-integer `bit_rate`
- [x] 3.3 Write chunk files before probing so the first chunk is available to probe
- [x] 3.4 Extract `generateSilence` and encode the silence using the probed format, falling back to 48000 Hz mono 128000 bps
- [x] 3.5 Log a warning on every fallback path so silent degradation is visible

## 4. Frontend

- [x] 4.1 Add an "Enhanced Audio Quality" switch to the podcast settings page, shown when the TTS provider is Inworld
- [x] 4.2 Set `ttsSettings.enhanceGeneration` to `"true"` when enabled and delete the key when disabled

## 5. Tests

- [x] 5.1 Add `InworldApiClientTest` coverage for the `bitRate` field name, `enhanceGeneration` present/absent/false, `deliveryMode` replacing `temperature`, and `speed` mapping to `speakingRate`
- [x] 5.2 Add `InworldTtsProviderTest` coverage for `enhanceGeneration` passthrough, omission when unset, and rejection of non-boolean values
- [x] 5.3 Add `AudioConcatenatorTest` (first coverage for the class) asserting the output matches 44.1kHz and 48kHz chunks, stays uniform across multiple chunks, falls back with no chunks, and probes valid and invalid files
- [x] 5.4 Guard the concatenator tests with an ffmpeg/ffprobe availability assumption so they skip rather than fail where the tools are absent
- [x] 5.5 Run the full suite and confirm it is green (975 tests, 0 failures)

## 6. Verification

- [x] 6.1 Typecheck the frontend with `npx tsc --noEmit`
- [x] 6.2 Restart the application so the running instance serves the new build
