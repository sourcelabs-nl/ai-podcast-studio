## MODIFIED Requirements

### Requirement: Upcoming content page with tabbed layout
The system SHALL provide a page at `/podcasts/[podcastId]/upcoming` that uses a tabbed layout with Articles and Script tabs, matching the episode detail page pattern.

The header SHALL include an optional "Focus" text field next to the "Generate Episode" button. When the field is filled and generation is confirmed, the generate request SHALL include the entered text as `focus`. When the field is left empty, the generate request SHALL be sent exactly as before (no `focus` field), and generation behaves as a regular episode. Clicking "Generate Episode" SHALL first open a confirmation dialog, and the generate request SHALL only be sent once the user confirms it.

#### Scenario: Page header
- **WHEN** user navigates to the upcoming content page
- **THEN** the page displays a back link "Back to Episodes", a header "Upcoming Episode" with article count, source count, and next scheduled generation time (parsed from the podcast's cron expression), a "Generate Episode" button, and a "Focus" text field

#### Scenario: Next generation schedule
- **WHEN** the podcast has a cron expression configured
- **THEN** the header subtitle shows the next generation date and time (e.g., "Will be generated Wed, Mar 4 at 06:00 AM"), with the cron expression parsed using `tz: 'UTC'` to match the backend's UTC-based scheduling

#### Scenario: No articles
- **WHEN** the page loads and there are no upcoming articles or posts
- **THEN** the Articles tab displays a message indicating no content has been collected yet

#### Scenario: Generate with a focus filled in
- **WHEN** the user types "Claude Opus 5.5 release" into the Focus field, clicks "Generate Episode" and confirms
- **THEN** the generate request body includes `{"focus": "Claude Opus 5.5 release"}`

#### Scenario: Generate with the focus field empty
- **WHEN** the user clicks "Generate Episode" without filling in the Focus field and confirms
- **THEN** the generate request is sent with no `focus` field, exactly as it was before the Focus field existed

#### Scenario: Generating asks for confirmation first
- **WHEN** the user clicks "Generate Episode"
- **THEN** a confirmation dialog opens stating whether a regular or a focus episode will be generated (showing the focus text for a focus episode) and how many upcoming articles will be considered, with Cancel and Generate buttons
- **AND** no generate request is sent until the user confirms with Generate; Cancel closes the dialog without generating
