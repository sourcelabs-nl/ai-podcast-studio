## Context

The episode list is served by `EpisodeController.list`, which validates paging, maps status names to `EpisodeStatus`, and delegates to `EpisodeService.findByPodcastIdPaged`. That method picks between two Spring Data derived queries (`findByPodcastId` and `findByPodcastIdAndStatusIn`), both returning `Page<Episode>`.

Search does not fit a derived query. It has to reach across `episode_articles` and `articles`, restrict to covered stories, apply an arbitrary number of AND-ed terms, and still return a correctly counted page of episodes. The codebase already has a home for exactly this shape of query: `EpisodeArticleRepositoryCustom` / `EpisodeArticleRepositoryCustomImpl`, which uses `JdbcClient` with hand-written SQL for the joins that Spring Data cannot express.

## Goals / Non-Goals

**Goals:**

- Answer "which episodes covered X" from the archive that already exists, without a new pipeline stage or extraction step.
- Compose with the status filter and pagination instead of replacing them.
- Tell the user why each episode matched, so a strong topic hit is distinguishable from a passing mention.

**Non-Goals:**

- Ranking. Results stay in the list's existing reverse-chronological order, which is what "when did we cover this" wants. Relevance ranking is what FTS5 would buy, and it is not needed at this size.
- Highlighted snippets from the script. The match details name the topic and article, which is more useful than a fragment of dialogue and far cheaper to compute.
- Cross-podcast search. The endpoint is already scoped to one podcast and the screen asking for this is a single podcast's page.
- Searching articles that were gathered but never composed. An episode should only match on something a listener would have heard.

## Decisions

**Extend the existing list endpoint rather than adding a search endpoint.**

Search is a filter on the same collection, and it must combine with the status filter and paging that already live there. A separate `/episodes/search` would duplicate all of that validation and give the frontend two code paths that return the same shape. The frontend change stays small: `q` joins the query string next to `status` and `page`.

**`GLOB` over FTS5.**

164 episodes and 4,544 links are small enough that a scan is imperceptible. FTS5 would mean a virtual table, a migration, and triggers to keep the index synchronised with three tables on every write, which is a standing correctness liability for a corpus this size. Revisit when ranking is wanted, not before.

Matching uses `GLOB` rather than `LIKE` because only its `[^a-z]` character class can express a word boundary, and word boundaries turned out to be essential rather than cosmetic: with substring matching, 12 of the 21 episodes whose script mentioned "java" contained it only inside "JavaScript". The boundary excludes letters but deliberately not digits, so "qwen" still finds "Qwen3.8", where a model name runs into its version. The column is padded with spaces so a term at the very start or end of a field still has a boundary on both sides, and lowered on both sides because `GLOB` is case-sensitive.

`GLOB` has no `ESCAPE` clause, so its four metacharacters are escaped by wrapping each in a bracket expression, which matches it literally. This was verified against SQLite directly rather than assumed, including the awkward `[]]` form for a literal `]`.

**AND across whitespace-separated terms, substring match per term.**

A single substring match on the raw query makes `retrieval augmented` miss "retrieval-augmented", which is precisely the kind of near-miss that makes a search feel broken. Requiring every term to appear somewhere in the episode's searchable text keeps multi-word queries narrowing rather than widening, without needing a tokenizer.

**Restrict article matching to covered stories via `topic_order IS NOT NULL`.**

In the current archive that predicate selects 2,750 links, and every one of them also has a `topic` label, so it is exactly the set of stories that reached the script. Filtering on `topic_order` rather than on `topic` being non-null states the intent directly: this link was placed in the running order.

**Return match details from the same query.**

The episode row needs the matching topic labels and article titles, so the query aggregates them per episode rather than making the frontend issue a follow-up request per result. Script-only matches (an episode whose text matched but none of whose covered stories did) fall out naturally: the aggregated lists come back empty.

## Risks / Trade-offs

- **A leading-wildcard `GLOB` cannot use an index, so every search scans.** → Acceptable and measured: the tables are small, and the query is bounded by one podcast. If the archive grows by an order of magnitude this is the first thing to revisit.
- **Whole-word matching means a query is no longer a prefix search, so "kube" no longer finds "kubernetes".** → Accepted, because the reverse error is worse: a search for a short name silently returning mostly unrelated episodes reads as broken, while a query that needs the whole word is predictable. Digits not counting as boundaries preserves the version-suffix case, which is the common one in this domain.
- **Script text is the noisiest field and will produce matches on passing mentions.** → Kept deliberately, because it catches things the topic labels miss, but flagged in the response so the UI can label a script-only hit rather than presenting it as a covered story.
- **Episodes generated before topic labelling existed have no topics.** → They remain searchable through `script_text`, `recap`, and `show_notes`, so they degrade to script-only matches rather than disappearing.
