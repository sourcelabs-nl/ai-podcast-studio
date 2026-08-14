## Purpose

Defines the frontend page for previewing upcoming episode content and dry-run script preview.

## Requirements
### Requirement: Upcoming content page with tabbed layout
The system SHALL provide a page at `/podcasts/[podcastId]/upcoming` that uses a tabbed layout with Articles and Script tabs, matching the episode detail page pattern.

#### Scenario: Page header
- **WHEN** user navigates to the upcoming content page
- **THEN** the page displays a back link "Back to Episodes", a header "Upcoming Episode" with article count, source count, and next scheduled generation time (parsed from the podcast's cron expression), and a "Generate Episode" button

#### Scenario: Next generation schedule
- **WHEN** the podcast has a cron expression configured
- **THEN** the header subtitle shows the next generation date and time (e.g., "Will be generated Wed, Mar 4 at 06:00 AM"), with the cron expression parsed using `tz: 'UTC'` to match the backend's UTC-based scheduling

#### Scenario: No articles
- **WHEN** the page loads and there are no upcoming articles or posts
- **THEN** the Articles tab displays a message indicating no content has been collected yet

### Requirement: Always-visible upcoming episode link
The podcast detail page SHALL always display the "Next Episode" navigation link, regardless of whether articles are available.

#### Scenario: Articles available
- **WHEN** the podcast detail page loads and there are upcoming articles
- **THEN** the "Next Episode" link SHALL display with the article count (e.g., "Next Episode · 5 articles ready")

#### Scenario: No articles available
- **WHEN** the podcast detail page loads and there are no upcoming articles
- **THEN** the "Next Episode" link SHALL display with the text "Next Episode · no articles yet"

### Requirement: Articles tab
The Articles tab (default) SHALL display all articles collected for the next episode, grouped by source with collapsible sections.

#### Scenario: Articles displayed
- **WHEN** the Articles tab is active and there are upcoming articles
- **THEN** articles are fetched from `GET /api/users/{userId}/podcasts/{podcastId}/upcoming-articles` and displayed using the same grouped-by-source pattern as the episode detail Articles tab

### Requirement: Script tab with inline preview
The Script tab SHALL allow the user to generate a dry-run script preview inline via SSE, displaying real-time progress stages, word count, and estimated audio duration.

The client SHALL parse the stream so that an event whose `event:` and `data:` lines fall in different network chunks is still recognised, by carrying the current event name across reads. The progress display SHALL cover every stage the backend emits (`aggregating`, `scoring`, `deduplicating`, `composing`), and SHALL render scoring as a running count of scored articles against the total so a long scoring run visibly advances.

The proxy route serving the stream SHALL be dynamic and SHALL instruct intermediaries not to buffer, so progress reaches the browser as it happens rather than in one batch at the end.

#### Scenario: No preview generated yet
- **WHEN** the Script tab is selected and no preview has been generated
- **THEN** a centered "Preview Script" button is displayed with a message "No script preview generated yet."

#### Scenario: Preview Script clicked
- **WHEN** user clicks "Preview Script"
- **THEN** a GET SSE connection is opened to `/api/users/{userId}/podcasts/{podcastId}/preview`, a loading state is shown with the current pipeline stage (e.g., "Scoring articles... 120/598", "Deduplicating 76 articles...", "Composing script from 71 articles..."), and on receiving the `result` event the script is rendered inline using the ScriptContent component with word count and estimated duration (~150 words/minute) shown above the script

#### Scenario: Event split across a chunk boundary
- **WHEN** a progress event's `event:` line and `data:` line arrive in separate reads of the stream
- **THEN** the event is still handled and its stage label is displayed

#### Scenario: Deduplication stage displayed
- **WHEN** the pipeline emits `{stage: "deduplicating", articleCount: N}`
- **THEN** the loading state shows that N articles are being deduplicated

#### Scenario: Script tab label with word count
- **WHEN** a preview has been generated
- **THEN** the Script tab label shows the word count (e.g., "Script (2,450 words)")

#### Scenario: Preview with no content
- **WHEN** user clicks "Preview Script" but the `result` event contains a message instead of scriptText
- **THEN** the message is displayed to the user as an error banner

#### Scenario: Preview connection error
- **WHEN** the SSE connection fails or emits an error event
- **THEN** the loading state is cleared and an error message is displayed

### Requirement: Generate Episode action
The page header SHALL display a "Generate Episode" button visible from any tab.

#### Scenario: Generate Episode clicked
- **WHEN** user clicks "Generate Episode"
- **THEN** a POST request is made to `/api/users/{userId}/podcasts/{podcastId}/generate`, and on success the user is navigated to the new episode's detail page at `/podcasts/{podcastId}/episodes/{episodeId}`

#### Scenario: Generate with no content
- **WHEN** user clicks "Generate Episode" but there are no relevant articles
- **THEN** the response message is displayed to the user as an error banner

