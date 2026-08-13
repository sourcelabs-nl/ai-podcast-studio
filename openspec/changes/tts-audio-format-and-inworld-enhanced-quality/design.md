## Context

`AudioConcatenator` stitches TTS chunks with ffmpeg's concat demuxer under `-c copy`. That is a stream copy: ffmpeg writes the frames through untouched and never reconciles inputs encoded differently. The prepended 500ms silence was generated with fixed parameters (`anullsrc=r=48000:cl=mono`, `-b:a 128k`), which only ever matched one provider by coincidence.

Provider output differs today: Inworld returns 48kHz mono 128k, ElevenLabs requests `mp3_44100_128`. So ElevenLabs episodes carried a sample rate change between the silence and the first speech frame, which players may reject.

The Inworld side had an unrelated defect discovered while investigating: `audioConfig.bitRateHertz` is not a field Inworld recognises, so our 128000 never applied. That it happened to produce 128000 anyway (Inworld's own default) is what kept the mismatch invisible for Inworld episodes.

## Goals / Non-Goals

**Goals:**
- The concatenated MP3 has a single uniform encoding regardless of TTS provider.
- The Inworld request uses the documented field name so the requested bitrate is honoured.
- Inworld's "Enhanced" audio quality (denoising) is available per podcast.

**Non-Goals:**
- Re-encoding episodes at concat time. Kept as a stream copy.
- Normalising providers onto one shared output format. Each keeps its own; only the silence adapts.
- Repairing already-generated episode files. Only new audio is affected.
- Stereo output. Inworld's `audioConfig` has no channel field and returns mono; duplicating a mono source would double the bytes for no added information.

## Decisions

**Probe the chunks and match the silence, rather than re-encoding the whole episode.**
Re-encoding (`-c:a libmp3lame` instead of `-c copy`) would also guarantee uniformity and need no probe, but it costs CPU on every episode and imposes a generation loss on audio that is already lossy. Matching the silence keeps the speech bytes untouched. The alternative of hardcoding a per-provider format was rejected because it re-creates the same coupling between two unrelated constants that caused this bug.

**Probe the first chunk, not every chunk.**
Chunks within one episode come from a single provider in one configuration, so they are uniform by construction. Probing all of them would cost an ffprobe process per chunk to detect a situation that would already be a provider bug.

**Fall back to the previous constants rather than failing.**
`probeFormat` returns null on a non-zero exit, unparseable output, or a `bit_rate` of `N/A` (which VBR streams report), and the caller then uses 48kHz mono 128k. A probe failure therefore reproduces the old behaviour instead of breaking generation. Silence generation failure already degraded to "no silence", and that is retained.

**Omit `enhanceGeneration` unless explicitly set.**
`ttsSettings` is a free-form string map, so the value is parsed with `toBooleanStrictOrNull`. Anything other than `"true"` or `"false"` yields null and the field is left out of the request entirely, letting Inworld apply its own default rather than us guessing an intent from a typo.

**Gate the UI switch on provider, not model.**
`deliveryMode` is gated to `inworld-tts-2` because Inworld documents it as TTS-2 only. `enhanceGeneration` carries no such documented restriction, so gating it on the model would hide a working option.

**Extract `buildSynthesisBody`.**
The request body was previously built inline in `synthesizeSpeech`. `MockRestServiceServer` cannot intercept these calls because `createClient` installs its own Reactor request factory per call, overriding the mock binding. Extracting the pure body-building function makes the wire format directly assertable without restructuring the HTTP plumbing.

## Risks / Trade-offs

- **`ffprobe` becomes a hard dependency for the matched-silence path** → It ships with `ffmpeg`, which is already required for concatenation and silence generation, so any environment that could produce audio before already has it. A missing `ffprobe` degrades to the fallback format rather than failing.
- **One extra process per episode** → A single ffprobe on a small MP3, against an episode that already spawns an ffmpeg per concatenation and many TTS HTTP calls. Negligible.
- **The Inworld bitrate is now genuinely configurable, where before it was inert** → Previously a wrong value was harmless because the field was ignored; now a wrong value would take effect. This is mitigated by the same change: the silence follows the chunks, so a bitrate change no longer desynchronises the output.
- **Verified against the live API, not just the docs** → `bitRate` was confirmed to be bits per second and honoured (64000 in, 64000 out), and `enhanceGeneration` was confirmed to leave sample rate, channel count and bitrate unchanged. Had denoising altered the encoding, it would have reintroduced the very mismatch this change fixes.
- **Existing episodes keep their original encoding** → Any already-published ElevenLabs episode that Spotify rejects needs regenerating via `POST /{episodeId}/regenerate-audio`.
