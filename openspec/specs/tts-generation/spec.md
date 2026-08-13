## Requirements

### Requirement: Text chunking at sentence boundaries
The system SHALL split the briefing script into chunks that respect a configurable maximum chunk size. The `TextChunker.chunk()` method SHALL accept a `maxChunkSize: Int` parameter (defaulting to 4096 for backward compatibility). Chunks SHALL be split at sentence boundaries (period, exclamation mark, question mark followed by whitespace) to avoid mid-sentence audio cuts.

#### Scenario: Long script split into multiple chunks
- **WHEN** a briefing script of 8000 characters is processed with `maxChunkSize = 4096`
- **THEN** the script is split into 2 or more chunks, each at most 4096 characters, with splits occurring at sentence boundaries

#### Scenario: Short script kept as single chunk
- **WHEN** a briefing script of 1500 characters is processed with `maxChunkSize = 2000`
- **THEN** the script is sent as a single chunk without splitting

#### Scenario: Very long sentence handling
- **WHEN** a single sentence exceeds the configured `maxChunkSize`
- **THEN** the sentence is split at the nearest whitespace before the limit

#### Scenario: Inworld chunk size used
- **WHEN** a script is chunked with `maxChunkSize = 2000`
- **THEN** each chunk is at most 2000 characters

#### Scenario: Default chunk size for backward compatibility
- **WHEN** `TextChunker.chunk(text)` is called without a `maxChunkSize` parameter
- **THEN** the default max chunk size of 4096 is used

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
