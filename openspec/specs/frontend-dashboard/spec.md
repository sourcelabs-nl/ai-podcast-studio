## Purpose

Defines the requirements for the Next.js web frontend dashboard that provides visual management of podcasts, episodes, and the episode approval workflow.
## Requirements
### Requirement: User picker
The system SHALL display a user picker dropdown in the header that fetches all users from `GET /users` and allows selecting one. The selected user context SHALL be used for all subsequent API calls. The dropdown popover SHALL align to the end (right) of the trigger. A gear icon button (Settings icon from lucide-react) SHALL be displayed to the right of the user dropdown, navigating to `/settings`. The icon button SHALL use the `ghost` variant with `text-primary-foreground` styling and SHALL have a `border border-input rounded-md h-9` style matching the user dropdown trigger's border appearance. The icon button SHALL include a `title` attribute with text "User settings".

#### Scenario: User selection persists across navigation
- **WHEN** user selects a user from the picker and navigates between pages
- **THEN** the selected user remains active and all API calls use that user's ID

#### Scenario: No users available
- **WHEN** the user picker fetches from `GET /users` and receives an empty list
- **THEN** the picker SHALL display a message indicating no users are available

#### Scenario: Settings icon navigates to preferences
- **WHEN** the user clicks the gear icon button next to the user dropdown
- **THEN** the app navigates to `/settings`

#### Scenario: Settings icon hidden when no user selected
- **WHEN** no user is selected (loading or no users available)
- **THEN** the gear icon button SHALL NOT be displayed

#### Scenario: Settings icon has tooltip
- **WHEN** the user hovers over the gear icon button
- **THEN** a tooltip with text "User settings" is displayed

### Requirement: Podcast overview page
The system SHALL display a vertical list of all podcasts for the selected user at the `/podcasts` route, fetched from `GET /users/{userId}/podcasts`. Each podcast row SHALL show the podcast name, an orange style badge (default variant), and topic aligned to the right. Each podcast card SHALL also display a gear icon-only button (Settings) using `size="icon-lg"` that navigates to `/podcasts/{podcastId}/settings` without triggering navigation to the podcast detail page. The button SHALL include a `title` attribute with text "Settings".

#### Scenario: Display podcasts
- **WHEN** a user is selected and the podcasts page loads
- **THEN** all podcasts for that user are displayed in a single-column list with name, orange style badge, topic, and an icon-only Settings button with hover tooltip

#### Scenario: Navigate to episode list
- **WHEN** user clicks on a podcast row (not on the gear icon)
- **THEN** the app navigates to `/podcasts/{podcastId}` showing that podcast's episodes

#### Scenario: Navigate to settings via gear icon
- **WHEN** user clicks the gear icon button on a podcast card
- **THEN** the app navigates to `/podcasts/{podcastId}/settings` and the click does NOT trigger navigation to the podcast detail page

#### Scenario: No podcasts
- **WHEN** the selected user has no podcasts
- **THEN** an empty state message is displayed

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

### Requirement: Approve episode
The system SHALL display an "Approve" button on episodes with status `PENDING_REVIEW`. Clicking the button SHALL call `POST /users/{userId}/podcasts/{podcastId}/episodes/{episodeId}/approve`.

#### Scenario: Successful approval
- **WHEN** user clicks "Approve" on a PENDING_REVIEW episode
- **THEN** the API is called, the episode status updates to APPROVED, and the button is removed

#### Scenario: Approve button visibility
- **WHEN** an episode has a status other than PENDING_REVIEW
- **THEN** the "Approve" button SHALL NOT be displayed

### Requirement: Discard episode
The system SHALL display a "Discard" button on episodes with status `PENDING_REVIEW`. Clicking the button SHALL call `POST /users/{userId}/podcasts/{podcastId}/episodes/{episodeId}/discard`.

#### Scenario: Successful discard
- **WHEN** user clicks "Discard" on a PENDING_REVIEW episode
- **THEN** the API is called and the episode status updates to DISCARDED

#### Scenario: Discard button visibility
- **WHEN** an episode has a status other than PENDING_REVIEW
- **THEN** the "Discard" button SHALL NOT be displayed

### Requirement: Badge text casing
All badges across the frontend SHALL render text in consistent lowercase using the `lowercase` CSS class applied to the badge base styles.

#### Scenario: Badge text is lowercased
- **WHEN** any badge is rendered (status badges, style badges, published badges)
- **THEN** the text is displayed in lowercase

### Requirement: Application title
The frontend SHALL display "AI Podcast Studio" as the application name in the header and page metadata (title and description).

#### Scenario: Header displays application name with icon
- **WHEN** any page is loaded
- **THEN** the header displays a Podcast icon (from lucide-react) followed by "AI Podcast Studio"

#### Scenario: Page metadata
- **WHEN** any page is loaded
- **THEN** the page title is "AI Podcast Studio" and description is "Dashboard for AI Podcast Studio"

### Requirement: Episode recap field
The `Episode` TypeScript interface SHALL include an optional `recap` field (string) for the episode summary.

#### Scenario: Recap available in type
- **WHEN** the frontend fetches episode data
- **THEN** the `recap` field is available on the Episode type for use in the publish wizard confirmation step

### Requirement: Upcoming episode bar on podcast detail page
The podcast detail page SHALL display a highlighted bar below the header description and above the tabs, showing the count of articles ready for the next episode. The bar SHALL link to `/podcasts/{podcastId}/upcoming`.

#### Scenario: Articles available
- **WHEN** the podcast detail page loads and there are relevant unprocessed articles
- **THEN** a bar is displayed below the description: "Next Episode · N articles ready" with a chevron, linking to the upcoming content page

#### Scenario: No articles available
- **WHEN** the podcast detail page loads and there are no relevant unprocessed articles
- **THEN** the upcoming episode bar is not displayed

### Requirement: Orange theme
The frontend SHALL use the shadcn/ui orange color theme with oklch color variables following the official shadcn theming documentation. All badges, buttons (default variant), and focus rings SHALL use the primary orange color for consistent branding.

#### Scenario: Consistent orange branding
- **WHEN** any component uses the `default` variant (Badge, Button)
- **THEN** it renders with the primary orange color (`oklch(0.705 0.187 47.604)` in light mode)

### Requirement: Bookmarkable tab links
All tabbed pages SHALL sync the active tab with a `?tab=X` URL query parameter. When the page loads with a valid `?tab` parameter, that tab SHALL be selected. When the user switches tabs, the URL SHALL be updated using `router.replace` (no new history entry). When no `?tab` parameter is present or the value is invalid, the default tab SHALL be selected.

#### Scenario: Tab from URL on page load
- **WHEN** a user navigates to `/podcasts/{podcastId}?tab=sources`
- **THEN** the Sources tab is active on load

#### Scenario: Tab updated in URL on switch
- **WHEN** a user clicks the "Publications" tab on the podcast detail page
- **THEN** the URL updates to `/podcasts/{podcastId}?tab=publications` without a full page reload and without adding a browser history entry

#### Scenario: Invalid tab parameter falls back to default
- **WHEN** a user navigates to `/podcasts/{podcastId}?tab=bogus`
- **THEN** the default tab (episodes) is selected

#### Scenario: No tab parameter uses default
- **WHEN** a user navigates to `/podcasts/{podcastId}` without a `?tab` parameter
- **THEN** the default tab (episodes) is selected

#### Scenario: Evaluation tab is bookmarkable
- **WHEN** a user navigates to `/podcasts/{podcastId}/episodes/{episodeId}?tab=evaluation`
- **THEN** the Evaluation tab is active on load

#### Scenario: All tabbed pages support bookmarkable tabs
- **WHEN** any of the 4 tabbed pages is loaded
- **THEN** tab state is synced with `?tab` query parameter:
  - `/podcasts/{podcastId}` — episodes (default), publications, sources
  - `/podcasts/{podcastId}/settings` — general (default), llm, tts, content, publishing
  - `/podcasts/{podcastId}/upcoming` — articles (default), script
  - `/podcasts/{podcastId}/episodes/{episodeId}` — script (default), articles, publications, costs, evaluation

### Requirement: Episode detail page GENERATING_AUDIO display
The episode detail page SHALL display appropriate UI for episodes in `GENERATING_AUDIO` status. The status badge SHALL show `GENERATING_AUDIO` with the `default` variant. No action buttons (approve, discard, publish, regenerate) SHALL be displayed while in this status.

#### Scenario: Episode detail in GENERATING_AUDIO status
- **WHEN** the episode detail page loads for an episode with status `GENERATING_AUDIO`
- **THEN** the page shows the episode header with a `GENERATING_AUDIO` badge (default variant), no action buttons, and the script/articles/publications tabs are available

#### Scenario: Episode detail transitions from GENERATING_AUDIO to GENERATED
- **WHEN** the episode is in `GENERATING_AUDIO` status and an `episode.generated` SSE event arrives
- **THEN** the page refreshes and shows the episode with status `GENERATED` and publish/discard action buttons

### Requirement: Delete podcast with typed-name confirmation
The podcast detail page SHALL provide a destructive "Delete podcast" action in a clearly separated danger zone. Activating it SHALL open a confirmation dialog that explains the deletion is permanent and cascades to all episodes, sources, and audio. The dialog SHALL require the user to type the exact podcast name; the confirm button SHALL remain disabled until the typed text matches the podcast name exactly. On confirmation the frontend SHALL call `DELETE /users/{userId}/podcasts/{podcastId}` and, on success, navigate to the podcast list.

#### Scenario: Confirm button disabled until name matches
- **WHEN** the user opens the delete dialog and the typed text does not exactly match the podcast name
- **THEN** the Delete confirm button is disabled

#### Scenario: Successful deletion redirects to list
- **WHEN** the user types the exact podcast name and clicks Delete, and the backend returns HTTP 204
- **THEN** the podcast is deleted and the user is navigated to the podcast list

#### Scenario: Cancel leaves podcast intact
- **WHEN** the user opens the delete dialog and clicks Cancel
- **THEN** the dialog closes and no delete request is sent

### Requirement: Episode evaluation tab
The episode detail page SHALL provide an Evaluation tab beside Script, Articles, Publications and Costs. The tab SHALL read the episode's stored evaluation data from `GET /users/{userId}/podcasts/{podcastId}/episodes/{episodeId}/scores`, `/metrics` and `/evaluation-runs`, and SHALL NOT trigger a scoring run.

The tab SHALL report, in four sections:

- **Attention score:** per stored score row, the overall score and the cliffhanger, humor and teaser component scores, each shown with the counts it was derived from (promises, deferred promises, unpaid promises, median deferral turns; humor beats, speaker balance, reaction ratio; distinct teaser topics). Each row SHALL be labelled with its judge model and scorer version, and rows from different scorers SHALL NOT be averaged together.
- **Script shape:** turn count, total words, and per role the turns held, the words held, the word share, the median and maximum turn length, and the number of turns over the compose prompt's sentence cap; plus laugh tags per role.
- **Anchors and outliers:** the judge's promises with the turn that makes each one and the turn that pays it off (or that it is unpaid), the judge's humor beats with their role and whether each reacts to the previous turn, and the metrics' backchannel candidates and same-speaker runs.
- **Run conditions:** per evaluation run, the prompt hash, variety selection, compose model, temperature, whether the cache was bypassed, whether it hit, and the tools that fired.

Figures the underlying data documents as proxies SHALL be labelled as such in the UI: laugh tags are a proxy for humor distribution and not a humor count, and a backchannel candidate is a turn shaped like a token of listening, not a defect.

#### Scenario: Evaluation tab shows a scored episode
- **WHEN** a user opens the Evaluation tab for an episode that has a stored score
- **THEN** the overall score and its three components are shown with their counts, labelled with the judge model and scorer version

#### Scenario: Script shape is reported per role
- **WHEN** the Evaluation tab is open for a multi-speaker episode
- **THEN** each role's turns, words, word share, median and maximum turn length, and turns over the sentence cap are shown

#### Scenario: Episode that was never scored
- **WHEN** a user opens the Evaluation tab for an episode with no stored score
- **THEN** the attention score section states that the episode has not been scored, and the other sections still render

#### Scenario: Episode with no evaluation runs
- **WHEN** a user opens the Evaluation tab for an episode with no evaluation run rows
- **THEN** the run conditions section states that no cache-bypassing run was recorded for this episode, distinctly from the not-scored state

#### Scenario: Multiple scorer versions
- **WHEN** an episode has score rows from more than one scorer version or judge model
- **THEN** the most recent row is shown expanded and the others are listed separately, never combined into one figure

#### Scenario: Tab does not trigger scoring
- **WHEN** the Evaluation tab is opened
- **THEN** only read requests are issued and no scoring run is started

### Requirement: Evaluation turn indices link into the script
Every turn index the Evaluation tab reports SHALL be a control that switches the page to the Script tab, scrolls that turn into view, and highlights it. This applies to judge promise and payoff turns, judge humor beats, backchannel candidates and same-speaker runs.

Turn indices SHALL be interpreted as 0-based positions over the same turn sequence the script viewer renders. When the parsed script has no turn at a reported index, that entry SHALL be shown without a jump control rather than scrolling to the wrong turn.

The highlight SHALL be cleared when the user switches tabs again.

#### Scenario: Jumping to a promise payoff
- **WHEN** a user clicks the payoff turn index of a promise in the Evaluation tab
- **THEN** the page switches to the Script tab, scrolls that turn into view and highlights it

#### Scenario: Jumping to a backchannel candidate
- **WHEN** a user clicks the turn index of a backchannel candidate
- **THEN** the page switches to the Script tab and highlights that turn

#### Scenario: Index outside the parsed script
- **WHEN** the tab reports a turn index that the parsed script does not contain
- **THEN** the entry is rendered without a jump control and no navigation happens

#### Scenario: Highlight does not persist
- **WHEN** a user jumps to a turn and then switches to another tab
- **THEN** the highlight is cleared
