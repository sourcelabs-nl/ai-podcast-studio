## MODIFIED Requirements

### Requirement: Silence prepended to audio output
The `AudioConcatenator` SHALL prepend 500ms of silence before the first audio chunk when concatenating TTS output. This prevents podcast players from clipping the first word of the episode.

Because concatenation is performed with ffmpeg's concat demuxer using `-c copy` (a stream copy that does not reconcile differing inputs), the silence SHALL be encoded to match the speech chunks. The concatenator SHALL probe the first audio chunk with `ffprobe` to read its sample rate, channel count, and bit rate, and SHALL generate the silence with those same parameters. A mismatch would produce a file with a mid-stream format change, which podcast platforms may reject.

The probe SHALL return no format, and the concatenator SHALL fall back to 48000 Hz mono at 128000 bps, when any of the following hold: there are no audio chunks, `ffprobe` exits non-zero, its output cannot be parsed, or the reported `bit_rate` is not an integer (variable bit rate streams report `N/A`). Each fallback SHALL be logged as a warning.

#### Scenario: Silence added to concatenated audio
- **WHEN** audio chunks are concatenated into the final MP3 file
- **THEN** a 500ms silent MP3 segment is generated via ffmpeg and prepended before the first audio chunk

#### Scenario: Silence matches 44.1kHz provider output
- **WHEN** the speech chunks are MP3 at 44100 Hz mono 128000 bps (as returned by ElevenLabs)
- **THEN** the prepended silence is generated at 44100 Hz mono 128000 bps and the concatenated file has a single uniform encoding throughout

#### Scenario: Silence matches 48kHz provider output
- **WHEN** the speech chunks are MP3 at 48000 Hz mono 128000 bps (as returned by Inworld)
- **THEN** the prepended silence is generated at 48000 Hz mono 128000 bps and the concatenated file has a single uniform encoding throughout

#### Scenario: Multiple chunks stay uniform
- **WHEN** several speech chunks at 44100 Hz mono 128000 bps are concatenated
- **THEN** the resulting file reports 44100 Hz mono 128000 bps with no mid-stream format change

#### Scenario: Probe failure falls back to default format
- **WHEN** `ffprobe` cannot determine the format of the first chunk
- **THEN** the silence is generated at 48000 Hz mono 128000 bps and a warning is logged

#### Scenario: No chunks falls back to default format
- **WHEN** concatenation is invoked with an empty list of audio chunks
- **THEN** the silence is generated at 48000 Hz mono 128000 bps

#### Scenario: Silence generation failure
- **WHEN** ffmpeg fails to generate the silence segment
- **THEN** the concatenation proceeds without silence and a warning is logged
