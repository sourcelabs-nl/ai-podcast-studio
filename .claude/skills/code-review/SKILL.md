---
name: code-review
description: Use when reviewing code changes - PR reviews, pre-commit reviews, or automated workflows. Enforces architectural rules for Spring Boot/Kotlin codebases.
user-invocable: true
context: fork
agent: code-reviewer
argument-hint: "[path|--all]"
---

# Code Review

Review Kotlin code with these arguments: $ARGUMENTS

## Task

Run a code review for architectural violations, Spring Data JDBC issues, database inconsistencies, testing anti-patterns, and Jackson 3.x migration issues.

## Scope

Determine what to review based on the arguments above:
- If arguments contain **--all**: Glob for all `**/*.kt` files under `src/main/kotlin`. Do NOT check git diff.
- If arguments contain a **path** (e.g., `src/main/kotlin/`): Glob for all `.kt` files under that path.
- If **no arguments** were provided (empty): Review files changed vs the main branch. Run `git diff --name-only main...HEAD -- '*.kt'`. If on `main` with no changes, tell the user to pass a path or `--all` to review existing code.

For each file in scope, read it fully before applying rules. Also read relevant Flyway migrations (`src/main/resources/db/migration/`) when checking database rules.

## Rules

Apply rules based on file types:

- **All `.kt` files**: Apply rules from the `architecture` skill (A1-A7), `kotlin-quality` skill (K1-K9), and `spring-boot` skill (SB1-SB7), plus the inline rules below (Controller Hygiene, Service Layer, Testing, Jackson 3.x, Concurrency, LLM Prompt Grounding).
- **`@Table`-annotated entities (e.g. under `store/`), `*Repository.kt`, `*.sql` files**: Additionally apply rules from the `spring-data-jdbc` skill and `database-design` skill (DB1-DB5). Use the `flyway-migration` skill to validate migration files. (Note: this codebase combines domain and entity in one class and does not use the `*Entity.kt` suffix; match `@Table` instead of the filename.)
- **Jackson-related files**: Use the `jackson-migration` skill as reference for Jackson 2.x vs 3.x patterns.

### Controller Hygiene

Controllers must validate input, delegate to service or domain classes, and map responses. They must not contain business logic.

**Acceptable in controllers:**
- Input validation (null checks, format validation, returning 400)
- Authorization checks (ownership validation, returning 403/404)
- Calling service methods
- Mapping domain objects to response DTOs via extension functions (`.toResponse()`)
- Returning appropriate HTTP status codes (201 for creation, 202 for async, 409 for conflicts)

**Violations to flag:**
- Multi-step orchestration logic (should be in a service)
- Direct repository calls for mutations (save/delete) -- reads for simple lookups are acceptable
- Domain calculations or business rule evaluation
- Duplicating logic that already exists in a service

### Service Layer

Services are the single home for business logic. When the same operation can be triggered from multiple entry points (API endpoint, scheduler), the shared logic must live in a single service method, all entry points call that method.

**Violations to flag:**
- Duplicate logic: reimplementing something that already exists in another service method
- Multiple entry points (controller + scheduler) implementing the same operation with duplicated logic instead of sharing a service method
- Missing `@Transactional` on methods that call save/delete on multiple repositories
- Bypassing a service to directly access a repository owned by another service (e.g., calling `articleRepository` from `EpisodeService` instead of going through `ArticleService`, if one exists)

### Testing: MockK Only

This project uses **MockK** (not Mockito) for all Kotlin tests, and `@MockkBean` from `springmockk` (`com.ninja-squad:springmockk`) for Spring integration tests.

**Violations to flag:**
- Imports from `org.mockito`
- Use of `@MockBean` (Spring's Mockito-based annotation) instead of `@MockkBean`
- Use of Mockito syntax (`when(...).thenReturn(...)`) instead of MockK syntax (`every { ... } returns ...`)

### Jackson 3.x

This is a Spring Boot 4 project using Jackson 3.x. The `com.fasterxml.jackson.annotation` package is unchanged in 3.x and is still correct.

**Violations to flag:**
- Imports from `com.fasterxml.jackson.databind` (should be `tools.jackson.databind`)
- Imports from `com.fasterxml.jackson.core` (should be `tools.jackson.core`)
- Use of `JsonSerializer`/`JsonDeserializer` (should be `ValueSerializer`/`ValueDeserializer`)
- Use of `JsonProcessingException` (should be `JacksonException`)

**Not a violation:**
- Imports from `com.fasterxml.jackson.annotation` -- these are correct in Jackson 3.x

### Concurrency: Coroutines Only

This project uses Kotlin coroutines for all async/background work. No Java concurrency primitives are allowed.

**Violations to flag:**
- Use of `ExecutorService`, `Executors`, `ThreadPoolExecutor`, or any `java.util.concurrent` thread pool
- Use of `Thread()` or `thread {}` for async work
- Use of `CompletableFuture` for async orchestration (coroutines should be used instead)
- Using `Dispatchers.Default` for I/O-bound work (must use `Dispatchers.IO` for HTTP requests, database calls, file I/O)

**Not a violation:**
- `Semaphore` from `kotlinx.coroutines.sync` (coroutine-aware concurrency primitive)

### LLM Prompt Grounding

Composer prompts (briefing, dialogue, interview) must include grounding instructions that constrain LLM output to the provided article content only.

**Violations to flag:**
- A new or modified composer prompt that lacks explicit instructions to only use provided article content
- Removal of existing grounding constraints from prompts
- Prompts that encourage the LLM to add external knowledge or speculation

## Before Reporting: Deduplicate and Calibrate

**Deduplicate by root cause.** A single underlying issue often trips several rules at once (e.g. a `.name` string passed to a `@Query` overload trips K1 *type-safety*, A4 *domain logic*, and a Spring Data JDBC *unjustified `@Query`* rule). Report it **once**, under the most specific rule, with a single concrete fix. Cross-reference the other rule IDs in one line (`also relates to: K1, A4`) instead of emitting a separate finding per rule. The summary count must reflect distinct issues, not rule-hits.

**Group repeated patterns.** When the same issue appears in N files, report it once as "same pattern in N files" and list the locations, rather than repeating the finding.

**Calibrate severity with this rubric** (do not waffle: pick one and state the fix):
- **violation** — breaks a stated rule and has a concrete, unambiguous fix in this codebase's conventions (e.g. Mockito import, `@Transactional` missing on a multi-write method, direct repository call from a scheduler where a service method exists).
- **warning** — a real issue, but the fix depends on context or judgment, or the impact is conditional (e.g. an in-memory sort that is cheap today but won't scale, a full-table fetch behind pagination).
- **note** — style, future-proofing, or a sanctioned-but-noteworthy pattern. **Use `note` (not `violation`) for correct uses of a sanctioned API** even when it superficially resembles a banned construct: e.g. `TaskScheduler`/`ScheduledFuture` under K7, a singleton config table's non-null `@Id` under Spring Data JDBC Rule 4, a `@Query` with `LIMIT`. If you find yourself revising a severity mid-write, that is the signal it is a `note`, resolve it before emitting.

## Output Format

Group findings by category. For each finding, include:

```
### <Category>

**<severity>** `<file_path>:<line_number>` — <Rule name>
<Description of the issue and suggested fix>
```

Severities:
- **violation** -- must fix, breaks architectural rules
- **warning** -- should fix, potential issue
- **note** -- consider, style or best practice suggestion

If no issues are found, confirm the reviewed files pass all checks.

At the end, provide a summary count: `X violations, Y warnings, Z notes across N files reviewed`.
