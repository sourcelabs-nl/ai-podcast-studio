## MODIFIED Requirements

### Requirement: Episode list page
The system SHALL display a list of episodes for a podcast at `/podcasts/{podcastId}` within a tabbed layout, fetched from `GET /users/{userId}/podcasts/{podcastId}/episodes`. Each episode row SHALL be a clickable link that navigates to `/podcasts/{podcastId}/episodes/{episodeId}`. Each episode row SHALL show the episode ID, generated date, day of week (short format), status badge, and action buttons. The episodes list SHALL be displayed under the "Episodes" tab, which is the default active tab. A "Publications" tab SHALL be displayed alongside it. The Date column SHALL have a fixed width. All badge text SHALL be consistently lowercased. The podcast detail header SHALL display the podcast name with the style badge inline next to it, and an icon-only "Settings" button right-aligned. The podcast detail header SHALL display the topic and cron schedule combined on one line in `text-sm` format, with the cron schedule in human-readable form separated by a dot separator (e.g., `{topic} · at 03:00 PM, Monday through Friday`). When the podcast has a non-UTC timezone, the timezone SHALL be displayed after the cron description (e.g., `{topic} · at 03:00 PM, Monday through Friday (Europe/Amsterdam)`). The status filter SHALL be integrated into the Status column header as a dropdown menu, rather than as a standalone select component above the table. All action buttons (Settings, Details, Publish, Approve) SHALL be icon-only using `size="icon-lg"` with `title` attributes for hover tooltips. Destructive buttons (Discard) SHALL keep the `destructive` variant, be icon-only, and include a `title` attribute. Episodes with status `GENERATING` SHALL be displayed as the first row with a spinner icon and the current pipeline stage text in abbreviated form (e.g., "Scoring..."). Episodes with status `GENERATING_AUDIO` SHALL be displayed with a spinner icon and "Generating audio..." text, similar to the `GENERATING` display. `GENERATING` and `GENERATING_AUDIO` episodes SHALL NOT have action buttons. The row SHALL use a subtle visual indicator (e.g., primary border highlight) to distinguish it from completed episodes.

#### Scenario: Click episode row navigates to detail page
- **WHEN** user clicks on an episode row
- **THEN** the app navigates to `/podcasts/{podcastId}/episodes/{episodeId}`

#### Scenario: Display episodes with columns
- **WHEN** the episode list page loads
- **THEN** episodes are displayed under the "Episodes" tab with columns: #, Date (fixed width), Day (short weekday name in muted text, e.g., "Mon"), Status (badge with optional Published badge), Script Model (compose model in `text-xs`, min-width column), TTS Model (`text-xs`, min-width column), Cost (right-aligned, combined LLM + TTS cost formatted as dollars, or em dash when unavailable), and Actions

#### Scenario: GENERATING episode in list
- **WHEN** an episode has status `GENERATING` with `pipelineStage` "scoring"
- **THEN** it appears as the first row with a spinner and "Scoring..." text (abbreviated stage name), no action buttons

#### Scenario: GENERATING_AUDIO episode in list
- **WHEN** an episode has status `GENERATING_AUDIO`
- **THEN** it appears with a spinner and "Generating audio..." text, no action buttons, and the row uses a subtle visual indicator

#### Scenario: GENERATING_AUDIO episode transitions to GENERATED
- **WHEN** a GENERATING_AUDIO episode's status changes to `GENERATED` via SSE event
- **THEN** the episode list refreshes and shows the episode with status GENERATED and appropriate action buttons

#### Scenario: GENERATING episode transitions to complete
- **WHEN** a GENERATING episode's status changes to `PENDING_REVIEW` or `GENERATED` via SSE event
- **THEN** the episode list refreshes and shows the episode with its final status and action buttons

#### Scenario: Status filter includes GENERATING_AUDIO
- **WHEN** the status filter dropdown is opened
- **THEN** `GENERATING_AUDIO` is included as a selectable status option alongside other statuses

#### Scenario: Cron schedule display with timezone
- **WHEN** the podcast detail page loads and the podcast has a cron schedule and timezone `Europe/Amsterdam`
- **THEN** the header area displays the cron expression converted to human-readable text followed by the timezone in parentheses

#### Scenario: Cron schedule display with UTC timezone
- **WHEN** the podcast detail page loads and the podcast has a cron schedule and timezone `UTC`
- **THEN** the header area displays the cron expression without a timezone suffix (UTC is the implicit default)

#### Scenario: Countdown timer uses podcast timezone
- **WHEN** the podcast detail page displays a countdown to the next scheduled generation
- **THEN** the cron expression SHALL be parsed with `tz` set to the podcast's `timezone` field to match the backend's timezone-aware scheduling

#### Scenario: Action buttons are icon-only with tooltips
- **WHEN** action buttons are rendered on episode rows or the podcast header
- **THEN** all buttons are icon-only (no text labels) with `title` attributes providing hover alt text

#### Scenario: Status badge for GENERATING_AUDIO
- **WHEN** an episode has status `GENERATING_AUDIO`
- **THEN** no status badge is shown (spinner with text is displayed instead, same as GENERATING)

## ADDED Requirements

### Requirement: Episode detail page GENERATING_AUDIO display
The episode detail page SHALL display appropriate UI for episodes in `GENERATING_AUDIO` status. The status badge SHALL show `GENERATING_AUDIO` with the `default` variant. No action buttons (approve, discard, publish, regenerate) SHALL be displayed while in this status.

#### Scenario: Episode detail in GENERATING_AUDIO status
- **WHEN** the episode detail page loads for an episode with status `GENERATING_AUDIO`
- **THEN** the page shows the episode header with a `GENERATING_AUDIO` badge (default variant), no action buttons, and the script/articles/publications tabs are available

#### Scenario: Episode detail transitions from GENERATING_AUDIO to GENERATED
- **WHEN** the episode is in `GENERATING_AUDIO` status and an `episode.generated` SSE event arrives
- **THEN** the page refreshes and shows the episode with status `GENERATED` and publish/discard action buttons