## Why

Inworld returns each chunk at whatever level the voice happens to produce, and the
pipeline never reconciles those levels. `AudioConcatenator` stream-copies the chunks
together, so every per-voice difference lands unchanged in the published episode.

Measured on six dialogue turns from episode 141, synthesized with the configured
interviewer voice (`Jason`) against two cloned expert voices:

| expert voice | expert turns (integrated) | interviewer turns | gap |
|---|---|---|---|
| `__stephan` | -26.1 / -25.1 / -25.5 LUFS | -21.1 / -20.3 / -20.8 LUFS | ~4.8 LU |
| `__stephan_1` | -21.7 / -22.7 / -22.3 LUFS | -21.4 / -21.3 / -20.5 LUFS | ~1.1 LU |

A gap of roughly 1 LU is already audible as "one speaker sounds quieter"; at 4.8 LU
the listener reaches for the volume control at every turn change. The effect is a
near-constant offset per voice, not variation within a voice, because it follows the
voice and holds across that voice's turns.

The episode as a whole also lands at whatever absolute level the voices produce, so
episodes differ from each other and from other podcasts in a listener's queue.

## What Changes

The pipeline measures the loudness of each voice's audio and applies one gain
correction per voice before concatenation, bringing every voice to a common target.
Correcting per voice rather than per chunk or per turn removes the offset while
leaving the dynamics within and between turns untouched: a deliberately quiet line
stays quieter than the line it follows.

Normalization requires decoding and re-encoding, so concatenation stops being a
stream copy. The format-matching the stream copy demanded (probing the first chunk to
encode the silence identically) is replaced by encoding every segment to one format.

Monologue episodes carry a single voice and so have no offset to correct, but they
receive the same correction toward the target so that absolute level is consistent
across episodes.

## Capabilities

### New Capabilities

None. This changes the behavior of existing audio assembly.

### Modified Capabilities

- `tts-generation`: adds a requirement that audio is loudness-normalized per voice
  before concatenation, and revises the existing "Silence prepended to audio output"
  requirement, whose format-probing and fallback rules exist to serve a `-c copy`
  concatenation that no longer applies.
- `tts-provider-abstraction`: an audio chunk carries the voice that produced it, and
  `TtsResult` drops the `requiresConcatenation` flag.
- `elevenlabs-tts`: the same two changes, plus how a multi-speaker dialogue batch is
  tagged.
- `inworld-tts`: the same two changes.

The `requiresConcatenation` flag goes because every path now runs through the
concatenator, including a single chunk: that is where levelling and the leading silence
are applied, and a single-chunk episode previously skipped both. Nothing reads the flag
once it stops deciding that, and `audioChunks.size` still answers the question it asked.

## Impact

- `AudioConcatenator` (`src/main/kotlin/com/aisummarypodcast/tts/AudioConcatenator.kt`):
  gains the normalization step; concatenation re-encodes rather than stream-copies.
- `TtsResult` (`TtsDtos.kt`): audio chunks must carry which voice produced them, which
  `InworldTtsProvider.generateDialogue` currently discards when it flattens turns into
  a single chunk list.
- `TtsProvider` implementations (`InworldTtsProvider`, `ElevenLabsTtsProvider`,
  `ElevenLabsDialogueTtsProvider`, `OpenAiTtsProvider`) populate the per-chunk voice.
- Callers of `AudioConcatenator.concatenate`: `TtsPipeline`, `PreviewAudioService`,
  `PreviewAudioStore`.
- `AudioConcatenatorTest` and the TTS provider tests.
- ffmpeg is already a hard dependency; normalization adds no new tooling.
- Generation gets slower: re-encoding replaces a stream copy, and measuring loudness
  means an extra pass over the audio.
