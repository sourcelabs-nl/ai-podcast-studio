## MODIFIED Requirements

### Requirement: Episode sources.md generation
The system SHALL generate a standalone HTML file for each episode during the episode creation pipeline. The file SHALL be stored at `data/episodes/{podcastId}/{slug}-sources.html` where `slug` is derived from the audio file name or generated from the episode timestamp.

The HTML file SHALL contain:
1. A `<meta charset="UTF-8">` declaration to ensure correct rendering of Unicode characters (smart quotes, IPA phonemes, etc.)
2. Inline CSS for clean, readable browser rendering (system fonts, max-width, orange accent links)
3. A heading with the podcast name
4. The episode date
5. The episode recap (summary) if available
6. A "Sources" heading followed by a list of clickable article links

All text content SHALL be HTML-escaped to prevent XSS.

#### Scenario: Sources file generated during pipeline
- **WHEN** an episode is created with a recap and 5 linked articles
- **THEN** a `sources.html` file is generated containing a valid HTML page with UTF-8 charset, the podcast name, date, recap, and all 5 sources as clickable links

#### Scenario: Sources file generated without recap
- **WHEN** an episode is created but recap generation fails
- **THEN** the `sources.html` file is generated without the summary section, containing only the podcast name, date, and sources

#### Scenario: Sources file generated without articles
- **WHEN** an episode is created with a recap but no linked articles
- **THEN** the `sources.html` file is generated with the podcast name, date, and recap, but no sources section

#### Scenario: Feed description links to HTML sources
- **WHEN** the RSS feed is generated
- **THEN** each episode description SHALL contain a "Sources:" link pointing to the `.html` file (not `.txt`)
