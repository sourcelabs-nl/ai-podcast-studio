## ADDED Requirements

### Requirement: Episode search on the podcast detail page
The podcast detail page at `/podcasts/{podcastId}` SHALL display a search input right-aligned on the same row as the Episodes / Publications / Sources tab list, so the row carries the tabs on the left and the search on the right.

Because the search filters the episode list only, the input SHALL be rendered only while the Episodes tab is active, rather than sitting above content it does not filter. When a search is active, the match count SHALL appear immediately to the left of the input on that same row.

The input SHALL be URL-synced as the `q` query parameter through the same mechanism as the status filter, so a search is bookmarkable, survives a reload, and is restored when navigating back. Typing SHALL be debounced before the request is issued, so a query is not sent on every keystroke. Changing the search SHALL reset paging to page 0, as changing the status filter already does. The input SHALL offer a way to clear the current search.

The search value SHALL be sent to `GET /users/{userId}/podcasts/{podcastId}/episodes` as `q`, alongside the existing `status`, `page`, and `pageSize` parameters, so search, status filtering, and pagination all apply together and the result count comes from the server.

When a search is active, the page SHALL show how many episodes matched, and SHALL show an empty state that mentions the query when nothing matched.

#### Scenario: Searching filters the list
- **WHEN** the user types a keyword into the search input
- **THEN** after the debounce interval the episode list refetches with `q` set and shows only matching episodes

#### Scenario: Search is bookmarkable
- **WHEN** the user searches and then reloads the page
- **THEN** the search input is repopulated from the URL and the same filtered results are shown

#### Scenario: Search resets paging
- **WHEN** the user is on page 3 and enters a search
- **THEN** the list returns to page 0 of the matching episodes

#### Scenario: Search combines with the status filter
- **WHEN** a status filter is active and the user enters a search
- **THEN** both are sent to the endpoint and only episodes satisfying both are listed

#### Scenario: Clearing the search restores the full list
- **WHEN** the user clears the search input
- **THEN** `q` is removed from the URL and the unfiltered episode list is shown

#### Scenario: No results
- **WHEN** a search matches no episodes
- **THEN** an empty state naming the query is displayed instead of the table

### Requirement: Episode rows show why they matched a search
When a search is active, each episode row SHALL display the match details returned by the endpoint, so the user can see why the episode was returned without opening it.

Matching topic labels SHALL be displayed under the row. Matching article titles SHALL be displayed when there are no matching topics, or alongside them, so a hit is always attributable to something concrete. An episode whose `matches.scriptOnly` is true SHALL be labelled as having matched only in the script text, visually distinct from a covered-story match, because a passing mention in dialogue is weaker evidence than a story the episode was built around. When the endpoint caps a match list, the row SHALL indicate that more matches exist rather than silently showing only the first few.

Match details SHALL NOT be rendered when no search is active, leaving the row exactly as it is today.

#### Scenario: Topic match shown on the row
- **WHEN** an episode matched on two covered topics
- **THEN** both topic labels are displayed under that episode's row

#### Scenario: Script-only match labelled
- **WHEN** an episode's `matches.scriptOnly` is true
- **THEN** the row indicates the match came only from the script text, distinct from a topic match

#### Scenario: Capped match list indicated
- **WHEN** an episode's match list was capped by the endpoint
- **THEN** the row indicates that further matches exist

#### Scenario: No match details without a search
- **WHEN** no search is active
- **THEN** episode rows render without any match details
