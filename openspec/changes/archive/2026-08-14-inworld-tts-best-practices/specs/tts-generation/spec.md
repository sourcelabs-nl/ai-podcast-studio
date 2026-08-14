## MODIFIED Requirements

### Requirement: Text chunking at sentence boundaries
The system SHALL split the briefing script into chunks that respect a configurable maximum chunk size. The `TextChunker.chunk()` method SHALL accept a `maxChunkSize: Int` parameter (defaulting to 4096 for backward compatibility).

Splits SHALL be attempted at the most natural boundary available, trying separators in priority order and only descending to the next when a piece still exceeds the maximum:
1. Paragraph break (a blank line)
2. Line break
3. Sentence boundary (period, exclamation mark, or question mark followed by whitespace)
4. Space

An unbreakable run longer than `maxChunkSize` SHALL be cut at the maximum as a last resort. Separators SHALL stay attached to the text that precedes them, so paragraph and line structure survives chunking instead of being flattened into single spaces. Every returned chunk SHALL be trimmed and non-empty, and SHALL be at most `maxChunkSize` characters.

#### Scenario: Paragraph breaks preferred over sentence boundaries
- **WHEN** a script of several paragraphs is chunked and a paragraph break is available within the limit
- **THEN** the split occurs at the paragraph break rather than mid-paragraph at a sentence boundary

#### Scenario: Sentence boundaries used within a long paragraph
- **WHEN** a single paragraph exceeds `maxChunkSize`
- **THEN** it is split at sentence boundaries

#### Scenario: Long script split into multiple chunks
- **WHEN** a briefing script of 8000 characters is processed with `maxChunkSize = 4096`
- **THEN** the script is split into 2 or more chunks, each at most 4096 characters

#### Scenario: Short script kept as single chunk
- **WHEN** a briefing script of 1500 characters is processed with `maxChunkSize = 1900`
- **THEN** the script is sent as a single chunk without splitting

#### Scenario: Very long sentence handling
- **WHEN** a single sentence exceeds the configured `maxChunkSize`
- **THEN** the sentence is split at whitespace before the limit

#### Scenario: Unbreakable run handling
- **WHEN** a single run with no whitespace exceeds `maxChunkSize`
- **THEN** it is cut at `maxChunkSize` rather than exceeding the limit

#### Scenario: Inworld chunk size used
- **WHEN** a script is chunked with `maxChunkSize = 1900`
- **THEN** each chunk is at most 1900 characters

#### Scenario: Default chunk size for backward compatibility
- **WHEN** `TextChunker.chunk(text)` is called without a `maxChunkSize` parameter
- **THEN** the default max chunk size of 4096 is used
