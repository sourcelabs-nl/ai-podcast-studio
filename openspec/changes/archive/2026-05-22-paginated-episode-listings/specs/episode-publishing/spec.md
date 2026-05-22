## ADDED Requirements

### Requirement: Paginated podcast-level publications endpoint
A new endpoint `GET /users/{userId}/podcasts/{podcastId}/publications` SHALL return the publications across all episodes of the podcast, paginated. It SHALL accept `page` (0-indexed, default 0) and `pageSize` (`[1, 200]`, default 20). The response SHALL be a `PagedResponse<PodcastPublicationRow>` ordered by publication `created_at DESC, id DESC`.

Each row SHALL contain:
- `publication`: the standard `PublicationResponse` (id, episodeId, target, status, externalId, externalUrl, errorMessage, publishedAt, createdAt).
- `episode`: a lightweight reference `{ id, generatedAt, status }` so the UI can render the source episode's date and id without a second fetch.

#### Scenario: Page across a podcast's publications
- **WHEN** a podcast has 96 publication rows across its episodes and the client requests page 0 size 20
- **THEN** the response contains 20 items, `total=96`, `totalPages=5`, each item with both `publication` and `episode` populated

#### Scenario: Empty podcast
- **WHEN** the podcast has no episodes (or no publications)
- **THEN** the response is `{ "items": [], "page": 0, "pageSize": 20, "total": 0, "totalPages": 0 }`

#### Scenario: Pagination bounds rejected
- **WHEN** `?page=-1` or `?pageSize=300` is requested
- **THEN** the request SHALL be rejected with HTTP 400

### Requirement: Publications tab uses paginated endpoint
The Publications tab on the podcast detail page SHALL fetch publications via the new podcast-level endpoint, NOT via per-episode fan-out. It SHALL render a paginator below the table (page size 10/20/50/100, default 20). The tab SHALL NOT include a status filter (multi-select filtering is scoped to Episodes only).

#### Scenario: Tab fetches a single paginated request
- **WHEN** the user opens the Publications tab
- **THEN** a single request `GET .../publications?page=0&pageSize=20` is issued (not N per-episode requests)

#### Scenario: Single-episode reuse
- **WHEN** the same `PublicationsTab` component is rendered on the episode detail page with an `episodeId` prop
- **THEN** it falls back to the existing per-episode endpoint and does not render the paginator
