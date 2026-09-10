## Why

On 2026-09-10 the pipeline shipped a published episode built from **1 of 138 candidate articles**,
and separately failed two episodes outright on a database constraint. Both incidents share one
shape: a stage produced a result that was obviously wrong, and the pipeline carried it forward
instead of stopping.

- Episode 203 failed twice with `UNIQUE constraint failed: post_articles.post_id,
  post_articles.article_id`. Two aggregation paths (the eager scoring after a poll, and the episode
  pipeline) derive the same article from the same posts concurrently, so the second writer loses
  the race on a link row that already exists.
- Episode 204's dedup call returned well-formed JSON naming 34 `NEW` clusters, 33 of which carried
  an empty `selectedArticleIds`. The prompt permits an empty selection only for `CONTINUATION`
  topics, so this response broke its own contract. Nothing checked it: the episode composed from
  the single surviving article, generated audio, and auto-published to SoundCloud and FTP.

The dedup filter already guards the *truncated* response path, requiring a salvage to still select
`compose.max-articles` articles. A response that parses cleanly is trusted unconditionally. That
asymmetry is what let a one-article episode reach listeners.

## What Changes

- **Aggregation link and article writes become idempotent.** `SourceAggregator` links a post to an
  article through an `INSERT OR IGNORE`, and resolves a duplicate article on
  `UNIQUE(source_id, content_hash)` by re-reading the stored row instead of failing the run. Two
  aggregations of the same posts converge on one article and one link rather than killing an
  episode.
- **The dedup filter validates every response against the prompt's own contract**, not just the
  salvaged ones. A `NEW` cluster that selects no article is invalid; when the majority of `NEW`
  clusters are empty the response is degenerate and the stage raises, handing its retry a turn and
  ultimately failing the episode loudly. A small minority of empty `NEW` clusters costs only those
  topics and is logged.
- **Auto-publish refuses to ship an episode below a minimum article count**, configurable via
  `app.publishing.min-articles`. The episode stays `GENERATED` for manual review instead of going
  out automatically.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `source-aggregation`: aggregation writes are idempotent under concurrent runs.
- `article-dedup-filter`: a parsed response is validated for degeneracy, not only a salvaged one.
- `episode-auto-publishing`: auto-publish is gated on a minimum article count.

## Impact

- `src/main/kotlin/com/aisummarypodcast/source/SourceAggregator.kt`
- `src/main/kotlin/com/aisummarypodcast/store/PostArticleRepository.kt` and a new
  `PostArticleRepositoryCustom` carrying the `INSERT OR IGNORE`
- `src/main/kotlin/com/aisummarypodcast/llm/TopicDedupFilter.kt`
- `src/main/kotlin/com/aisummarypodcast/publishing/AutoPublishListener.kt`
- `src/main/resources/application.yaml` and `AppProperties` for the new publishing floor
- No database migration: both constraints already exist and are now respected rather than tripped.
- Behaviour change worth naming: an episode that would previously have been published with almost
  no content now either fails in the dedup stage or stops before auto-publish. Failing loudly is
  the point, but it does mean a quiet day can leave an episode awaiting manual publication.
