## MODIFIED Requirements

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
