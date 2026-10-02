# Design

## Context

See proposal.md. Rule DB3 asks for filtering, sorting and counting in the database. The affected callers are small, so this is about not growing a pattern, and every change must return exactly what the in-memory code returned.

## Goals / Non-Goals

**Goals:** the listed callers stop filtering, sorting, counting or fetching one by one in memory, with identical results.

**Non-Goals:** rewriting `@Query` methods whose SQL is clearer than a derived name; splitting `EpisodeService`; changing any signature that other packages call.

## Decisions

- Prefer a derived query; use `@Query` only where a derived name cannot express it (for example a status set combined with `focus IS NULL`). New `@Query` methods follow the neighbouring style.
- `listByPodcast` needs the page of publications ordered as today; the query joins publications to episodes of the podcast and takes the page there. The existing ordering and the page metadata (total) stay the same.
- A replaced repository method that nothing else calls is removed; one that other code calls stays.
- Each new query gets a repository test against SQLite with rows that include the exclusion cases (other podcast, other status, experiment purpose).

## Risks / Trade-offs

- [A derived query orders or filters differently from the old in-memory code] -> The tests cover the edge rows, and the service tests that stub the old method are updated to the new one.
