## ADDED Requirements

### Requirement: Aggregation is idempotent under concurrent runs

Aggregation runs from two paths that can overlap on the same posts: the eager scoring that follows
a source poll, and the episode generation pipeline. Both derive the same article from the same
posts, so the same article row and the same post-to-article link can be written twice.

Aggregating a set of posts SHALL be idempotent. Linking a post to an article it is already linked
to SHALL leave a single link and SHALL NOT raise. Persisting an article whose
`(source_id, content_hash)` is already stored SHALL resolve to the stored article rather than
raising, so both runs converge on one article. A concurrent aggregation of the same posts SHALL
NOT fail the episode.

#### Scenario: The same post is linked to the same article twice

- **WHEN** a post is linked to an article it is already linked to
- **THEN** exactly one `post_articles` row exists for that pair and no error is raised

#### Scenario: Two runs aggregate the same posts concurrently

- **WHEN** the eager scoring after a poll and the episode pipeline aggregate the same unlinked posts
  at the same time
- **THEN** both runs complete, one article exists per distinct content hash, each post is linked to
  it once, and neither run fails the episode

#### Scenario: An article with a stored content hash is reused

- **WHEN** aggregation produces an article whose `(source_id, content_hash)` is already stored
- **THEN** the stored article is used and its posts are linked to it, rather than the run failing on
  the uniqueness constraint
