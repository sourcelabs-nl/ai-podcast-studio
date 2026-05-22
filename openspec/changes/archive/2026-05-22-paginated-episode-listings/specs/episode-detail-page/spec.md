## ADDED Requirements

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
