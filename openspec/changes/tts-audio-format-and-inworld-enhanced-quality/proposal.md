## Why

Episodes are assembled with ffmpeg's concat demuxer under `-c copy`, a stream copy that never reconciles differing inputs, yet the prepended 500ms silence was hardcoded to 48kHz mono 128k. ElevenLabs returns `mp3_44100_128`, so every ElevenLabs episode began with a mid-stream sample rate change that players are free to reject, and Spotify does.

Separately, the Inworld request sent `audioConfig.bitRateHertz`, which is not a field Inworld recognises (the documented name is `bitRate`). Our 128000 was silently discarded and we received Inworld's default instead. Inworld also exposes an "Enhanced" audio quality toggle in its studio that we had no way to enable.

## What Changes

- `AudioConcatenator` probes the first speech chunk with `ffprobe` and encodes the prepended silence to match its sample rate, channel count and bitrate, so the concatenated stream is uniform for every TTS provider.
- The probe falls back to the previous 48kHz mono 128k constants when there are no chunks, when `ffprobe` exits non-zero, when its output is unparseable, or when `bit_rate` reads `N/A` (VBR), so behaviour degrades to what it was rather than failing.
- `InworldSynthesisOptions` carries an optional `enhanceGeneration`, forwarded as the `enhanceGeneration` request field, which applies denoising to reduce background noise and artifacts.
- `InworldTtsProvider` reads `ttsSettings["enhanceGeneration"]` via `toBooleanStrictOrNull`, so a value that is neither `"true"` nor `"false"` is omitted rather than guessed.
- The podcast settings page shows an "Enhanced Audio Quality" switch whenever the TTS provider is Inworld.
- The Inworld `audioConfig` bitrate field is renamed from `bitRateHertz` to `bitRate`, so the requested 128000 bps is actually applied.
- Inworld request body construction moves into `InworldApiClient.buildSynthesisBody` so it can be asserted directly in tests.

Not breaking: no persisted data changes, no API contract changes, and `enhanceGeneration` is absent from requests unless a podcast opts in.

## Capabilities

### New Capabilities

None. Both affected behaviours already have capabilities.

### Modified Capabilities

- `inworld-tts`: adds the `enhanceGeneration` option and corrects the `audioConfig` bitrate field name from `bitRateHertz` to `bitRate`.
- `tts-generation`: the prepended silence must match the encoding of the speech chunks rather than using fixed parameters.

## Impact

- `src/main/kotlin/com/aisummarypodcast/tts/AudioConcatenator.kt` — new `AudioFormat` type, `probeFormat`, `generateSilence`.
- `src/main/kotlin/com/aisummarypodcast/tts/InworldApiClient.kt` — `enhanceGeneration` option, `buildSynthesisBody`, `bitRate` rename.
- `src/main/kotlin/com/aisummarypodcast/tts/InworldTtsProvider.kt` — reads the new setting.
- `frontend/src/app/podcasts/[podcastId]/settings/page.tsx` — the switch.
- Tests: `AudioConcatenatorTest` (new, first coverage for that class), `InworldApiClientTest`, `InworldTtsProviderTest`.
- External dependency: relies on `ffprobe` alongside the existing `ffmpeg` requirement.
- Existing episode files are unaffected; only newly generated audio changes.
