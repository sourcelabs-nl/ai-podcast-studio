# Capability: Episode Detail Page

## Purpose

Episode detail page in the dashboard showing script, articles, publications, and per-stage cost breakdown for a single episode.
## Requirements

### Requirement: Paged response envelope for list endpoints
List endpoints that support pagination SHALL return a JSON envelope of the form `{ items: T[], page: number, pageSize: number, total: number, totalPages: number }` (called `PagedResponse<T>` on the backend). `page` SHALL be zero-indexed. The envelope SHALL be the public API schema; the framework `org.springframework.data.domain.Page<T>` type SHALL NOT be returned from controllers.

#### Scenario: Envelope shape
- **WHEN** a paginated endpoint returns 3 items out of 109 with page 0 and pageSize 3
- **THEN** the response is `{ "items": [...3 entries], "page": 0, "pageSize": 3, "total": 109, "totalPages": 37 }`

#### Scenario: Empty result
- **WHEN** a paginated endpoint matches no rows
- **THEN** the response is `{ "items": [], "page": 0, "pageSize": <requested>, "total": 0, "totalPages": 0 }`

### Requirement: Paginated episodes list endpoint
`GET /users/{userId}/podcasts/{podcastId}/episodes` SHALL accept `page` (0-indexed integer, default 0) and `pageSize` (integer in `[1, 200]`, default 20) query parameters. The response SHALL be a `PagedResponse<EpisodeResponse>` ordered by `generated_at DESC, id DESC`.

#### Scenario: Default pagination
- **WHEN** a client calls `GET .../episodes` with no query params on a podcast with 109 episodes
- **THEN** the response contains 20 items, `page=0`, `pageSize=20`, `total=109`, `totalPages=6`

#### Scenario: Page beyond last page
- **WHEN** a client requests `?page=999` on a podcast with 109 episodes
- **THEN** the response contains 0 items and `total=109` (no error)

#### Scenario: Negative page rejected
- **WHEN** `?page=-1` is requested
- **THEN** the request SHALL be rejected with HTTP 400

#### Scenario: Out-of-range pageSize rejected
- **WHEN** `?pageSize=0` or `?pageSize=201` is requested
- **THEN** the request SHALL be rejected with HTTP 400

### Requirement: Multi-select status filter on episodes list
The episodes list endpoint SHALL accept the `status` query parameter zero or more times. Each value SHALL be a valid `EpisodeStatus` enum name. When one or more statuses are supplied, the result SHALL be filtered to episodes whose status is in that set. When the parameter is absent, no status filter is applied.

#### Scenario: Single status value
- **WHEN** `?status=GENERATED` is requested
- **THEN** only episodes with status GENERATED are returned

#### Scenario: Multiple status values
- **WHEN** `?status=GENERATED&status=FAILED` is requested
- **THEN** the response contains only episodes with status GENERATED or FAILED, paginated normally

#### Scenario: Invalid status value
- **WHEN** `?status=BOGUS` is requested
- **THEN** the request SHALL be rejected with HTTP 400 listing the valid values

### Requirement: Episodes UI with paginator and multi-select status
The podcast detail page's Episodes tab SHALL render a paginator below the table with: a page-size selector offering 10, 20, 50, and 100 (default 20), a "from-to of total" indicator, and Previous/Next buttons. The Status column header SHALL be a multi-select dropdown allowing any combination of `EpisodeStatus` values plus an "All statuses" reset option. The current `page`, `pageSize`, and `status[]` SHALL be persisted in the URL query string so links and the browser back button work.

#### Scenario: Page navigation persists in URL
- **WHEN** the user clicks "Next" from page 0
- **THEN** the URL becomes `?...&page=1&pageSize=20...` and the table refreshes with the next page

#### Scenario: Multi-status selection
- **WHEN** the user checks both "generated" and "failed" in the status dropdown
- **THEN** the URL becomes `?status=GENERATED&status=FAILED&page=0&...` and the table shows episodes in either status, page reset to 0

#### Scenario: Changing page size resets to page 0
- **WHEN** the user changes page size from 20 to 50 while on page 3
- **THEN** the URL updates to `?pageSize=50&page=0&...` so the user is not stranded on a non-existent page

### Requirement: Episode detail page route
The system SHALL provide a page at `/podcasts/[podcastId]/episodes/[episodeId]` that displays detailed information about a single episode.

#### Scenario: Navigate to episode detail
- **WHEN** user clicks an episode row in the episodes table
- **THEN** the app navigates to `/podcasts/{podcastId}/episodes/{episodeId}`

#### Scenario: Back navigation
- **WHEN** user is on the episode detail page
- **THEN** a back link labeled "Back to Episodes" is displayed that navigates to `/podcasts/{podcastId}`

### Requirement: Episode detail header
The episode detail page SHALL display a header section following this layout order: (1) episode number + status badge + Published badge (if published) on the first line, (2) a single `text-sm` muted line containing: "Generated {date} ({weekday})" followed by word count and estimated TTS duration (at 150 words per minute), actual audio duration (if available), and recap (if available), all separated by `·`. The status badge SHALL use `outline` variant for GENERATED and `default` (orange) for other active statuses. The Published badge SHALL use the `default` variant (orange).

#### Scenario: Header with full metadata
- **WHEN** the episode detail page loads for an episode with script text and audio
- **THEN** the header displays: "Generated {date} ({weekday}) · {N} words · ~{M} min estimated · duration {H}:{MM}" in `text-sm` muted text, followed by recap if available (separated by `·`)

#### Scenario: Header with published badge
- **WHEN** the episode has been published
- **THEN** an orange "Published" badge (default variant) is displayed next to the status badge

#### Scenario: Header word count and estimated duration
- **WHEN** the episode has script text
- **THEN** the word count is computed from the plain text (HTML tags stripped) and the estimated TTS duration is calculated at 150 words per minute

#### Scenario: Header with recap
- **WHEN** the episode has a recap field
- **THEN** the recap text is displayed inline after the duration, separated by `·`

#### Scenario: Header with error message for FAILED episode
- **WHEN** the episode has status `FAILED` and an `errorMessage` field is present
- **THEN** the error message is displayed in red (`text-destructive`) below the metadata line

### Requirement: Episode detail tabbed layout
The episode detail page SHALL display three tabs: "Script" (default active), "Articles", and "Publications".

#### Scenario: Default tab is Script
- **WHEN** the episode detail page loads
- **THEN** the "Script" tab is active and displays the episode script in chat-bubble style

#### Scenario: Switch to Articles tab
- **WHEN** user clicks the "Articles" tab
- **THEN** the articles for this episode are loaded and displayed

#### Scenario: Switch to Publications tab
- **WHEN** user clicks the "Publications" tab
- **THEN** the publications for this episode are displayed

### Requirement: Script tab
The Script tab SHALL render the episode's `scriptText` using the same chat-bubble rendering as the former script viewer dialog: monologue styles as paragraph bubbles, dialogue/interview styles as alternating left/right chat bubbles with speaker labels.

#### Scenario: Monologue script rendering
- **WHEN** the Script tab is active for a podcast with style `news-briefing`, `casual`, `deep-dive`, or `executive-summary`
- **THEN** each paragraph is rendered in a rounded card bubble with `bg-muted` background, using `text-sm` for body text

#### Scenario: Dialogue script rendering
- **WHEN** the Script tab is active for a podcast with style `dialogue` or `interview`
- **THEN** the script is parsed for speaker tags and rendered as alternating left/right chat bubbles, using `text-sm` for body text

### Requirement: Articles tab with grouped display
The Articles tab SHALL fetch articles from the episode articles API endpoint and display them grouped by source. Each source group SHALL be collapsible. Articles within each group SHALL be sorted by relevance score descending.

#### Scenario: Articles grouped by source
- **WHEN** the Articles tab is activated
- **THEN** articles are fetched from `GET /api/users/{userId}/podcasts/{podcastId}/episodes/{episodeId}/articles` and displayed in collapsible groups by source

#### Scenario: Article count in tab label
- **WHEN** articles have been loaded
- **THEN** the Articles tab label displays the count, e.g., "Articles (37)"

#### Scenario: Empty articles
- **WHEN** the episode has no linked articles
- **THEN** the Articles tab displays a message indicating no articles are linked

### Requirement: Article card display
Each article in the Articles tab SHALL display the article title, source label (or URL-derived fallback), relevance score with color coding, truncated summary (expandable), and a link to the original URL.

#### Scenario: Article card with full data
- **WHEN** an article has title, summary, relevance score, and URL
- **THEN** the card displays the title, a color-coded relevance score badge, truncated summary, and an external link icon/button

#### Scenario: Expand article summary
- **WHEN** user clicks on a truncated summary
- **THEN** the full summary text is revealed

#### Scenario: Source label fallback
- **WHEN** a source has no label set (null)
- **THEN** the display name is derived from the source URL (e.g., domain name or path)

### Requirement: Relevance score color coding
Article relevance scores SHALL be color-coded: scores 7-10 use green, scores 4-6 use amber/yellow, scores 1-3 use muted/grey.

#### Scenario: High relevance score
- **WHEN** an article has a relevance score of 8
- **THEN** the score badge is displayed in green

#### Scenario: Medium relevance score
- **WHEN** an article has a relevance score of 5
- **THEN** the score badge is displayed in amber/yellow

#### Scenario: Low relevance score
- **WHEN** an article has a relevance score of 2
- **THEN** the score badge is displayed in muted grey

### Requirement: Episode action buttons
The episode detail page header SHALL display action buttons appropriate to the episode status: "Approve" (PENDING_REVIEW only), "Discard" (PENDING_REVIEW only), "Publish" (GENERATED and unpublished only), "Discard" (GENERATED and not published to any target).

#### Scenario: Pending review actions
- **WHEN** the episode has status PENDING_REVIEW
- **THEN** "Approve" and "Discard" buttons are displayed

#### Scenario: Generated episode actions
- **WHEN** the episode has status GENERATED and is not published
- **THEN** "Publish" and "Discard" buttons are displayed

#### Scenario: Generated published episode actions
- **WHEN** the episode has status GENERATED and is published to at least one target
- **THEN** only the "Publish" button is displayed (no Discard)

### Requirement: Costs tab on episode detail page
The episode detail page SHALL include a "Costs" tab alongside Script, Articles, and Publications. The tab SHALL render the breakdown returned by the episode detail API as a table with columns Stage / Model / Calls / Input tokens / Output tokens / Cost. The rows SHALL be: Scoring, Dedup, Compose, Recap, TTS, Research, plus a Total footer row. Each cost cell SHALL be formatted in dollars with 4 decimal places (e.g. `$0.0042`) so sub-cent stage totals remain visible; cells with cost 0 SHALL render as `—`.

#### Scenario: Costs tab shows per-stage rows
- **WHEN** the user opens the Costs tab on an episode generated with all six stages
- **THEN** the table shows six rows (score, dedup, compose, recap, tts, research) with model name, calls, tokens, and cost; plus a Total row with the sum

#### Scenario: Legacy notice for pre-V57 episodes
- **WHEN** the user opens the Costs tab on an episode where all four LLM stage cost cells are 0 but TTS or research cost is non-zero
- **THEN** an italic notice "Detailed per-stage breakdown is not available for episodes generated before this feature shipped..." is shown above the table

#### Scenario: Costs tab handles missing data gracefully
- **WHEN** the API response omits the `costs` field (legacy response shape or error)
- **THEN** the tab renders an italic "Cost breakdown is not available for this episode." message instead of an empty table

#### Scenario: Total reflects sum of all rows
- **WHEN** the table renders
- **THEN** the Total footer equals the sum of all six rows' cost cents (formatted in dollars)

