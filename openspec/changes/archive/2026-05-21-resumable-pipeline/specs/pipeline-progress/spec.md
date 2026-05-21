## MODIFIED Requirements

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

## ADDED Requirements

### Requirement: Retry event notification
The system SHALL emit an `episode.retrying` SSE event when a failed episode retry is initiated. The event SHALL include the episode number and detected resume point. The frontend SHALL display a toast notification with the retry information.

#### Scenario: Retry event emitted
- **WHEN** a failed episode retry is initiated with resume point COMPOSE
- **THEN** an `episode.retrying` event is emitted with `resumePoint: "COMPOSE"` and `episodeNumber` set to the episode ID

#### Scenario: Retry toast displayed
- **WHEN** an `episode.retrying` event is received with `resumePoint: "COMPOSE"` and `episodeNumber: 82`
- **THEN** a toast notification displays "Retrying episode #82 from COMPOSE..."
