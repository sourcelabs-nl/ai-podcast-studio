## ADDED Requirements

### Requirement: Per-voice loudness normalization
Audio chunks SHALL carry the identifier of the voice that produced them, and the
`AudioConcatenator` SHALL bring every voice to a common loudness target before
concatenating.

The concatenator SHALL measure the integrated loudness (EBU R128) of each voice over
all of that voice's chunks taken together, and SHALL apply a single gain correction
per voice: the difference between that voice's measured loudness and the target of
-16 LUFS. Every chunk of a given voice SHALL receive the same correction.

Correcting per voice rather than per chunk or per turn is deliberate. The measured
problem is a near-constant offset between voices, so a single gain per voice removes
it while leaving each voice's own dynamics intact: a deliberately quiet line stays
quieter than the line before it, and a long turn split across chunks keeps its shape.

Applying gain alone can drive peaks above full scale, so the concatenator SHALL limit
the corrected audio so that the true peak of the output does not exceed -1.0 dBFS.
Because limiting pulls loudness back down by an amount that depends on how peaky the
voice is, the concatenator SHALL measure again after limiting and apply the residual
correction, so that the integrated loudness of every voice in the output lands within
0.5 LU of the target.

A single measure-and-apply pass with ffmpeg's `loudnorm` filter SHALL NOT be used for
this: in its linear mode the gain is capped by the peak ceiling, which leaves the
original offset largely in place.

Normalization SHALL be best-effort. When loudness cannot be measured or corrected for
a voice, the concatenator SHALL log a warning and concatenate that voice's chunks
uncorrected rather than failing the episode.

Audio measuring below -60 LUFS SHALL be left uncorrected with a warning. Such audio is
silence rather than a quiet voice (ffmpeg reports digital silence as -70 LUFS), and
correcting it toward the target would apply a boost of tens of decibels to nothing but
the noise floor.

The prepended silence SHALL be excluded from every loudness measurement.

#### Scenario: Two voices at different levels are matched
- **WHEN** an episode's interviewer chunks measure -21 LUFS and its expert chunks measure -26 LUFS
- **THEN** both voices are present in the output within 0.5 LU of -16 LUFS

#### Scenario: Dynamics within a voice are preserved
- **WHEN** one voice produces a chunk that is 4 LU quieter than another chunk of that same voice
- **THEN** that chunk remains quieter than the other in the output, by approximately the same difference

#### Scenario: Single voice is corrected to the target
- **WHEN** a monologue episode's only voice measures -24 LUFS
- **THEN** the output is within 0.5 LU of -16 LUFS

#### Scenario: Peaks are contained
- **WHEN** a voice needs a large positive gain to reach the target
- **THEN** the true peak of the output does not exceed -1.0 dBFS

#### Scenario: Silent audio is left alone
- **WHEN** a voice's chunks contain only silence
- **THEN** no gain is applied to them and a warning is logged

#### Scenario: Measurement failure does not fail the episode
- **WHEN** loudness measurement fails for a voice
- **THEN** that voice's chunks are concatenated without correction and a warning is logged

## MODIFIED Requirements

### Requirement: Silence prepended to audio output
The `AudioConcatenator` SHALL prepend 500ms of silence before the first audio chunk when concatenating TTS output. This prevents podcast players from clipping the first word of the episode.

Normalization requires decoding and re-encoding the speech chunks, so concatenation does not stream-copy its inputs and cannot inherit their encoding. The concatenator SHALL instead determine one target format for the whole output and encode every segment, silence included, to that format. The target format SHALL be read from the first audio chunk with `ffprobe`, so that a provider's native sample rate is preserved: ElevenLabs returns 44.1kHz, Inworld 48kHz.

The probe SHALL return no format, and the concatenator SHALL fall back to 48000 Hz mono at 128000 bps, when any of the following hold: there are no audio chunks, `ffprobe` exits non-zero, its output cannot be parsed, or the reported `bit_rate` is not an integer (variable bit rate streams report `N/A`). Each fallback SHALL be logged as a warning.

#### Scenario: Silence added to concatenated audio
- **WHEN** audio chunks are concatenated into the final MP3 file
- **THEN** a 500ms silent MP3 segment is generated via ffmpeg and prepended before the first audio chunk

#### Scenario: Silence matches 44.1kHz provider output
- **WHEN** the speech chunks are MP3 at 44100 Hz mono 128000 bps (as returned by ElevenLabs)
- **THEN** the whole output is encoded at 44100 Hz mono 128000 bps with a single uniform encoding throughout

#### Scenario: Silence matches 48kHz provider output
- **WHEN** the speech chunks are MP3 at 48000 Hz mono 128000 bps (as returned by Inworld)
- **THEN** the whole output is encoded at 48000 Hz mono 128000 bps with a single uniform encoding throughout

#### Scenario: Multiple chunks stay uniform
- **WHEN** several speech chunks at 44100 Hz mono 128000 bps are concatenated
- **THEN** the resulting file reports 44100 Hz mono 128000 bps with no mid-stream format change

#### Scenario: Probe failure falls back to default format
- **WHEN** `ffprobe` cannot determine the format of the first chunk
- **THEN** the output is encoded at 48000 Hz mono 128000 bps and a warning is logged

#### Scenario: No chunks falls back to default format
- **WHEN** concatenation is invoked with an empty list of audio chunks
- **THEN** the silence is generated at 48000 Hz mono 128000 bps

#### Scenario: Silence generation failure
- **WHEN** ffmpeg fails to generate the silence segment
- **THEN** the concatenation proceeds without silence and a warning is logged
