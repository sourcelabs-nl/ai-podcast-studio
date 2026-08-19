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

#### Scenario: Clearing takes effect immediately
- **WHEN** the user clears the search input
- **THEN** the match count and all match details disappear on the next render, without waiting for the debounce interval or for the URL to update, and the request that restores the unfiltered list is issued at once rather than after the debounce

#### Scenario: No results
- **WHEN** a search matches no episodes
- **THEN** an empty state naming the query is displayed instead of the table

### Requirement: Episode rows show why they matched a search
When a search is active, each episode row SHALL display the match details returned by the endpoint, so the user can see why the episode was returned without opening it.

Match details SHALL occupy at most two compact lines per episode, because an episode can match many topics and articles at once and rendering them all makes the result list unreadable.

Each line SHALL open with plain language naming what was found and where, quoting the user's query (for example `Found "Context Hub" in`), rather than presenting chips with no explanation of what the reader is looking at. When the only mention is in the episode's own text, the wording SHALL say so explicitly.

Match details SHALL be visually attached to the episode they describe: the row separator SHALL fall after the details rather than between the episode row and its details, and there SHALL be vertical space between the episode row and the details beneath it.

The details SHALL be presented as a subtly tinted panel with a border slightly darker than the table's own separators, so they read as commentary attached to the episode rather than as another row of table data. The tint SHALL work in both light and dark themes.

Matching topic labels SHALL be displayed as chips, each truncated to a bounded width with the full label available on hover, and the count of any topics beyond those shown SHALL be summarised rather than listed. Matching articles SHALL be summarised as a single count chip with the titles available on hover, not rendered inline: article titles are long and numerous, and listing them was what made the list messy.

Every label SHALL truncate rather than widen its row. The episode table SHALL NOT become wider than the page because of a long match label.

An episode whose `matches.scriptOnly` is true SHALL be labelled as having matched only in the script text, because a passing mention in dialogue is weaker evidence than a story the episode was built around. `matches.scriptContext` SHALL be shown on its own line, quoted and truncated to one line, with the full text on hover.

Every occurrence of a search term SHALL be emboldened wherever a match label or the script context is displayed, so the word that caused the match is findable at a glance in otherwise muted text. Highlighting SHALL use the same whole-word rule as the query (a letter ends a word, a digit does not), so it never emboldens a fragment the search did not match on: a search for "java" SHALL NOT embolden part of "JavaScript", while a search for "qwen" SHALL embolden the "Qwen" in "Qwen3.8".

The results SHALL be separated from the tab and search row by vertical space, rather than sitting directly beneath it.

Match details SHALL NOT be rendered when no search is active, leaving the row exactly as it is today.

#### Scenario: Topic match shown on the row
- **WHEN** an episode matched on two covered topics
- **THEN** both topic labels are displayed as chips under that episode's row

#### Scenario: Script-only match labelled
- **WHEN** an episode's `matches.scriptOnly` is true
- **THEN** the row indicates the match came only from the script text, distinct from a topic match

#### Scenario: Extra topics summarised rather than listed
- **WHEN** an episode's `topicTotal` exceeds the labels carried in `topics`
- **THEN** the row shows the displayed chips followed by a count of the remaining topics

#### Scenario: Articles summarised as a count
- **WHEN** an episode matched four articles
- **THEN** the row shows a single chip reading "4 articles" with the titles available on hover, rather than four title chips

#### Scenario: A long label does not widen the table
- **WHEN** a matching label is longer than the space available
- **THEN** it is truncated within its chip and the table stays within the page width

#### Scenario: Match details name what was found
- **WHEN** an episode matched a topic while the query was `Context Hub`
- **THEN** the details line reads `Found "Context Hub" in` ahead of the chips

#### Scenario: A script-only match says so in words
- **WHEN** an episode matched only through its own text
- **THEN** the details say the query was found only in the script, ahead of the quoted line

#### Scenario: Details attach to their own episode
- **WHEN** an episode row is followed by its match details
- **THEN** no separator is drawn between the two, and the separator falls after the details

#### Scenario: The search term is emboldened in the context line
- **WHEN** an episode's script context contains the search term
- **THEN** that term is rendered bold within the quoted line

#### Scenario: Highlighting does not embolden a partial word
- **WHEN** the query is `java` and a displayed line contains `JavaScript`
- **THEN** no part of `JavaScript` is emboldened

#### Scenario: No match details without a search
- **WHEN** no search is active
- **THEN** episode rows render without any match details
