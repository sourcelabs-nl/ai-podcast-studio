---
okf_version: "0.2"
type: reference
title: The SQLite driver returns no generated keys from a batch
answers: a save of several new rows fails with "After saving the identifier must not be null"
status: stable
method: >
  Two plain JDBC programs against xerial sqlite-jdbc 3.53.2.0, the version this
  project builds with. The first inserts one row through
  RETURN_GENERATED_KEYS and reads getGeneratedKeys(), then inserts three rows
  through addBatch/executeBatch and reads the same. The second inserts 200 rows
  one statement at a time and times the whole loop.
stale_after: 2027-09-22T00:00:00Z
generated:
  by: claude-opus-5
  at: 2026-09-22T00:00:00Z
---

Reading the identifiers back after an insert is what separates a save that works
from one that fails, and the driver answers the two ways of inserting
differently.

**What it does.** A single `executeUpdate` with `RETURN_GENERATED_KEYS` returns
one key. The same insert issued through `addBatch` / `executeBatch` writes every
row and then returns **no keys at all**: `getGeneratedKeys()` yields an empty
result set rather than one row per insert. JDBC permits this, since generated
keys after a batch are optional in the specification, so this is the driver
exercising a choice rather than a defect.

**Why it reaches this codebase.** Spring Data JDBC has batched `saveAll` of new
aggregates since 3.0. It issues one `executeBatch` and then populates each
aggregate's `@Id` from the returned keys, and it checks afterwards that the id
is not null. With no keys returned the check fails for every row, with
`IllegalArgumentException: After saving the identifier must not be null`. The
rows are in the table by then; the transaction that would have kept them rolls
back.

The failure only appears when several new aggregates are saved together. A test
that saves one row at a time passes, which is how
`episode_candidate_articles` shipped and stayed empty from the day it was added
until an episode failed on it.

**What to do instead.** Write the rows through an explicit
`@Modifying @Query("INSERT OR IGNORE ...")`, one call per row, which never asks
for the key. Batching buys nothing at this scale: 200 single-row inserts inside
one transaction take about 7.5 ms, against a table the pipeline writes a few
hundred rows to per episode. `INSERT OR IGNORE` also matches what a link table
means, the same line `EpisodeArticleRepository.insertIgnore` already holds: the
same fact stated twice is not a reason to fail an episode.

Applies to any `saveAll` of new aggregates with database-generated ids on
SQLite, not only to this table.
