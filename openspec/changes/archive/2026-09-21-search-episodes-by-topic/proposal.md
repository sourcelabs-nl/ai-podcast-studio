## Why

There is no way to answer "which episodes covered X". The episode list at `/podcasts/{podcastId}` can be filtered by status and paged, but with 164 episodes and roughly 17 covered stories each, finding when a topic was discussed means opening episodes one at a time.

The data to answer it is already there. The pipeline writes a per-story label to `episode_articles.topic` and marks the stories that made it into the script with `topic_order`: 2,750 links across the archive carry both. These read like real headings ("Recall trap in retriever configuration for code repair"), so a keyword search over them is a search over what the podcast actually talked about, with no new extraction and no new pipeline stage.

## What Changes

- The episode list endpoint gains an optional `q` parameter: `GET /users/{userId}/podcasts/{podcastId}/episodes?q=retrieval`. It composes with the existing `status` filter and pagination rather than replacing them, and `total` reflects the number of matching episodes.
- Matching is case-insensitive across the covered stories of an episode (`episode_articles.topic` and `articles.title` for links with a non-null `topic_order`) and the episode's own `script_text`, `recap`, and `show_notes`. Articles that were gathered and scored but never composed are NOT searched, so an episode only matches on something a listener would have heard.
- A multi-word query is split on whitespace and every term must match somewhere in that episode, so `retrieval augmented` finds an episode whose topic says "retrieval-augmented" without requiring that exact adjacency.
- Each matching episode carries a `matches` block naming why it matched: the matching topic labels, the matching article titles, and whether the only hit was in the script text.
- The episodes screen gains a debounced search box above the list, URL-synced as `q` so a search is bookmarkable and survives a reload, exactly as the status filter already is. Matching topics render under the episode row; a script-only match is labelled as such, so a passing mention is visually distinct from a covered story.

## Capabilities

### New Capabilities

- `episode-search`: keyword search over an archive's episodes, the fields it covers, how a query is interpreted, and the match details returned.

### Modified Capabilities

- `frontend-dashboard`: the episode list page gains the search box, the match details under each row, and the URL-synced `q` parameter.

## Impact

- Backend: `EpisodeController.list` (new `q` param and validation), `EpisodeService`, and a new custom repository following the `EpisodeArticleRepositoryCustomImpl` pattern (`JdbcClient`, hand-written SQL, because this is a join-and-aggregate query that derived query methods cannot express).
- Frontend: `frontend/src/app/podcasts/[podcastId]/page.tsx` (search input, `q` in `updateQuery` and the fetch query string, match details in the row).
- Database: no schema change and no migration. The query reads existing tables and columns.
- Performance: 164 episodes and 4,544 article links, so a `LIKE`-based scan is comfortably fast. SQLite FTS5 would add a virtual table, a migration, and triggers to keep it in sync, which is not justified at this size; it stays the upgrade path if ranked results or highlighted snippets are wanted later.
