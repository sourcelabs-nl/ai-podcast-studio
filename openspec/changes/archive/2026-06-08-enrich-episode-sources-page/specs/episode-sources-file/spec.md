## MODIFIED Requirements

### Requirement: Episode sources.md generation
The system SHALL generate a standalone HTML file for each episode during the episode creation pipeline. The file SHALL be stored at `data/episodes/{podcastId}/{slug}-sources.html` where `slug` is derived from the audio file path or generation timestamp.

The file SHALL contain:
1. A heading with the podcast name
2. The episode date
3. A metadata line showing, when available: the episode audio length (formatted human-readably from `durationSeconds`), the number of sources (the count of articles actually discussed when topic data is present, otherwise all linked articles), and a "Listen" link to the episode audio file. Parts with no data are omitted; the line is omitted entirely when no part has data.
4. A Summary section. The summary text SHALL be the episode `showNotes`, falling back to `recap`, falling back to a sanitized snippet of the script text (leaked preamble removed, speaker tags stripped while preserving spoken text, truncated). The Summary SHALL always be rendered.
5. A "Topics Covered" section with articles grouped by topic (when topic data is available), or a flat "Sources" list (when no topic data)

Article titles longer than 120 characters SHALL be truncated to 120 characters with "..." appended when rendered as link text in the HTML.

#### Scenario: Long article title truncated for display
- **WHEN** an article has a title of 200 characters
- **THEN** the link text in the HTML shows the first 120 characters followed by "..."

#### Scenario: Short article title not truncated
- **WHEN** an article has a title of 80 characters
- **THEN** the link text in the HTML shows the full title

#### Scenario: Title truncation does not affect URL or stored data
- **WHEN** an article title is truncated for display
- **THEN** the `<a href="...">` still links to the correct URL and the article title in the database is unchanged

#### Scenario: Metadata line shows duration, source count and listen link
- **WHEN** an episode with a known duration, 2 discussed articles, and an audio file is rendered
- **THEN** the page shows the formatted duration, "2 sources", and a "Listen" link pointing to the episode audio file name

#### Scenario: Source count reflects discussed articles
- **WHEN** an episode has 1 discussed article (non-null `topicOrder`) and 1 background article (null `topicOrder`)
- **THEN** the metadata line shows "1 source"

#### Scenario: Summary falls back to sanitized script when no recap or show notes
- **WHEN** an episode has no `showNotes` and no `recap` and its script begins with a leaked preamble and contains speaker tags
- **THEN** the Summary section renders the spoken text without any speaker tags or preamble

### Requirement: Generate sources.md for existing episodes
The system SHALL provide a mechanism (startup task or admin endpoint) to regenerate `sources.md` files for all existing episodes that have linked articles. This ensures existing episodes have sources files available for FTP upload.

#### Scenario: Regenerate for existing episodes
- **WHEN** the regeneration is triggered
- **THEN** all existing GENERATED episodes with linked articles get a `sources.md` file created in their podcast's data directory
