---
paths:
  - "src/main/kotlin/**/*Repository.kt"
---

# Repository authoring rules

Prefer Spring Data derived query methods. Reach for `@Query` only when a derived method cannot express the query.

**Do**
- Extend `CrudRepository<T, ID>`; add `PagingAndSortingRepository<T, ID>` only when pagination is needed.
- Name derived methods with typed parameters (pass enums, not `enum.name` strings) so the compiler validates them.
- When `@Query` is genuinely required, use named `:params` with `@Param`, and `@Modifying` for writes.

**Don't**
- Add `@Query` for a filter Spring Data can derive (e.g. `findByPodcastIdAndStatusIn`). A `@Query` taking `List<String>` statuses is a smell: prefer `Collection<EnumType>`.
- Use SQLite-unsupported casts (`::uuid`, `::text`).
- Write `ReadingConverter`/`WritingConverter` — enums persist as strings natively.
- Add `@Transactional` on repository methods (Spring Data already wraps each call).
- `@Query` with `LIMIT` is only borderline-justified; prefer `Top`/`First` derived names or `Pageable` when possible.

Full rules: `spring-data-jdbc` skill (Rules 6, 7, 8, 11) and `spring-boot` skill (SB2).
