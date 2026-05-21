## Purpose

Defines real-time SSE progress events emitted during the LLM episode generation pipeline, and the frontend rendering of those events on the podcast detail page.
## Requirements
### Requirement: Pipeline stage SSE events
The system SHALL emit `pipeline.progress` SSE events during episode generation at each LLM pipeline stage: `aggregating`, `scoring`, `deduplicating`, and `composing`. Each event SHALL include the stage name and relevant counts (postCount for aggregating, articleCount for scoring, deduplicating, and composing). Additionally, the system SHALL emit `episode.stage` events for intermediate state saves: `dedup_saved` (after dedup results persisted), `script_saved` (after script persisted), `marking_processed` (before marking articles), and `generating_recap` (before recap generation).

#### Scenario: Aggregating stage event
- **WHEN** the LLM pipeline begins aggregating unlinked posts
- **THEN** a `pipeline.progress` event is emitted with `stage: "aggregating"` and `postCount` set to the number of unlinked posts

#### Scenario: Scoring stage event
- **WHEN** the LLM pipeline begins scoring unscored articles
- **THEN** a `pipeline.progress` event is emitted with `stage: "scoring"` and `articleCount` set to the number of articles being scored

#### Scenario: Deduplicating stage event
- **WHEN** the LLM pipeline begins deduplicating eligible articles
- **THEN** a `pipeline.progress` event is emitted with `stage: "deduplicating"` and `articleCount` set to the number of eligible articles

#### Scenario: Composing stage event
- **WHEN** the LLM pipeline begins composing the briefing script
- **THEN** a `pipeline.progress` event is emitted with `stage: "composing"` and `articleCount` set to the number of relevant articles

#### Scenario: Dedup results saved event
- **WHEN** dedup results are persisted to episode_articles after dedup completes
- **THEN** an `episode.stage` event is emitted with `stage: "dedup_saved"` and `articleCount` set to the number of saved article links

#### Scenario: Script saved event
- **WHEN** the script is persisted to the episode after compose completes
- **THEN** an `episode.stage` event is emitted with `stage: "script_saved"`

#### Scenario: Marking processed event
- **WHEN** the system begins marking articles as processed during finalization
- **THEN** an `episode.stage` event is emitted with `stage: "marking_processed"` and `articleCount` set to the number of articles

#### Scenario: Generating recap event
- **WHEN** the system begins generating the recap during finalization
- **THEN** an `episode.stage` event is emitted with `stage: "generating_recap"`

### Requirement: Inline pipeline progress on podcast detail page
The podcast detail page "Next Episode" card SHALL display the current pipeline stage with a spinner when a `pipeline.progress` event is received. The card SHALL use a highlighted border (primary color) during active generation.

#### Scenario: Progress displayed during generation
- **WHEN** a `pipeline.progress` event with `stage: "scoring"` is received for the current podcast
- **THEN** the "Next Episode" card shows a spinner icon with "Scoring articles..." text and a primary-colored border

#### Scenario: Progress cleared after generation completes
- **WHEN** an `episode.created`, `episode.generated`, or `episode.failed` event is received after pipeline progress
- **THEN** the "Next Episode" card returns to its default state showing article counts and countdown

### Requirement: Pipeline status endpoint
The system SHALL provide a `GET /users/{userId}/podcasts/{podcastId}/pipeline-status` endpoint that returns the current pipeline stage for a podcast. An in-memory `PipelineStateTracker` component SHALL listen to `PodcastEvent` instances: it records the stage from `pipeline.progress` events and clears state on `episode.created`, `episode.generated`, or `episode.failed` events.

#### Scenario: Pipeline status when generation is active
- **WHEN** a `GET /users/{userId}/podcasts/{podcastId}/pipeline-status` request is received while the LLM pipeline is in the "composing" stage
- **THEN** the system returns HTTP 200 with `{"stage": "composing"}`

#### Scenario: Pipeline status when idle
- **WHEN** a `GET /users/{userId}/podcasts/{podcastId}/pipeline-status` request is received and no pipeline is running
- **THEN** the system returns HTTP 200 with `{"stage": null}`

### Requirement: Pipeline status fetched on page load
The podcast detail page SHALL fetch the pipeline status endpoint on initial page load alongside other data fetches (podcast, episodes, upcoming articles). If a non-null stage is returned, the "Next Episode" card SHALL immediately display the active pipeline stage with a spinner, without waiting for an SSE event.

#### Scenario: Page load during active generation
- **WHEN** the podcast detail page loads while the pipeline is in the "composing" stage
- **THEN** the "Next Episode" card immediately shows a spinner with "Composing script..." text and a primary-colored border

#### Scenario: Page load when idle
- **WHEN** the podcast detail page loads and no pipeline is running
- **THEN** the "Next Episode" card shows its default state with article counts and countdown

### Requirement: Toast notifications for pipeline progress
The system SHALL show toast notifications for `pipeline.progress` events with stage-specific messages: "Aggregating N posts...", "Scoring N articles...", "Deduplicating N articles...", "Composing episode script...". Additionally, toast notifications SHALL be shown for intermediate save events: "Saved N article topics", "Script saved", "Marking articles as processed...", "Generating recap...".

#### Scenario: Toast shown for scoring stage
- **WHEN** a `pipeline.progress` event with `stage: "scoring"` and `articleCount: 15` is received
- **THEN** a toast notification displays "Scoring 15 articles..."

#### Scenario: Toast shown for deduplicating stage
- **WHEN** a `pipeline.progress` event with `stage: "deduplicating"` and `articleCount: 20` is received
- **THEN** a toast notification displays "Deduplicating 20 articles..."

#### Scenario: Toast shown for dedup saved
- **WHEN** an `episode.stage` event with `stage: "dedup_saved"` and `articleCount: 15` is received
- **THEN** a toast notification displays "Saved 15 article topics"

#### Scenario: Toast shown for script saved
- **WHEN** an `episode.stage` event with `stage: "script_saved"` is received
- **THEN** a toast notification displays "Script saved"

#### Scenario: Toast shown for generating recap
- **WHEN** an `episode.stage` event with `stage: "generating_recap"` is received
- **THEN** a toast notification displays "Generating recap..."

### Requirement: Retry event notification
The system SHALL emit an `episode.retrying` SSE event when a failed episode retry is initiated. The event SHALL include the episode number and detected resume point. The frontend SHALL display a toast notification with the retry information.

#### Scenario: Retry event emitted
- **WHEN** a failed episode retry is initiated with resume point COMPOSE
- **THEN** an `episode.retrying` event is emitted with `resumePoint: "COMPOSE"` and `episodeNumber` set to the episode ID

#### Scenario: Retry toast displayed
- **WHEN** an `episode.retrying` event is received with `resumePoint: "COMPOSE"` and `episodeNumber: 82`
- **THEN** a toast notification displays "Retrying episode #82 from COMPOSE..."

