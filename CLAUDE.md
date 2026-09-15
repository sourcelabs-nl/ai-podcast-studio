# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Kotlin/Spring Boot application. See `README.md` for the full project description, architecture, prerequisites, and setup instructions.

## Running the Application

Use the provided scripts to start and stop the application:

- **Start:** `./start.sh`: runs the app in the background, logs to `app.log`, PID stored in `.app.pid`
- **Stop:** `./stop.sh`: gracefully stops the app (force-kills after 10s timeout)

Required environment variable (managed via direnv `.envrc`): `APP_ENCRYPTION_MASTER_KEY`. All provider credentials are managed via the UI/API.

## Testing

**Never leave the project in a broken state.** Every commit must compile and all tests must pass. When a code change breaks existing tests, fix those tests as part of the same change. Run `mvn test` before considering any change complete. If constructor signatures change, update all test files that instantiate the class.

## Code Review Loop

After fixing code review violations, always re-run the code reviewer (`/code-review --all`) to verify the fixes didn't introduce new violations and to catch issues that become visible after the first layer is cleaned up. Repeat until the review is clean. Fixes themselves can introduce new violations (e.g., moving logic to a service may reveal a missing `@Transactional`, or duplicating code during extraction).

## Architecture Guidelines

Controllers validate input, delegate to services, and map responses: no business logic. Never duplicate logic that already exists in a service.

The rules that apply to one kind of file live in `.claude/rules/`, keyed to the paths they govern, and load when such a file is touched: controllers, entities, repositories, schedulers, migrations, tests, `application.yaml`, main Kotlin sources (concurrency, transactions, parameter objects, Jackson) and `knowledge/` entries. For the full set of review rules see the `code-review` skill or run `/code-review`.

**Post-implementation check:** After every code change, validate that the architecture guidelines are respected, especially controller hygiene (no business logic, no direct repository access) and proper service layer delegation. Fix violations before considering the change complete.

## Code Navigation (LSP)

Prefer LSP over Grep/Glob for semantic navigation on `.kt` and `.ts`/`.tsx` files:

- **Use LSP for:** finding definitions (`goToDefinition`), references (`findReferences`), type info (`hover`), and file structure (`documentSymbol`).
- **Kotlin LSP limitations:** `goToImplementation`, `incomingCalls`, and `outgoingCalls` are not supported. Use `findReferences` as a fallback for these.
- **Use Grep/Glob for:** text-based searches (log messages, config keys, string literals), cross-codebase pattern matching, and finding files by name or path.

## Application Restart After Changes

Whenever code changes are made to the application, always restart it (`./stop.sh` then `./start.sh`) before testing or using the new feature. Never attempt to exercise a new or modified feature against a running instance that was built from old code.

## External API Integration

When adding or modifying calls to external APIs (Inworld, ElevenLabs, OpenAI, etc.), always verify the request payload against the actual API documentation before implementing. Proto/gRPC-based APIs often use string enums (e.g., `"ON"` / `"OFF"`) rather than booleans, so do not assume field types. After implementing an external API change, test it against the live API before considering the task complete.

OpenRouter reports its own cost per call, and the pipeline uses that provider-reported value wherever it is present, so the configured rates only matter as a fallback (a call that reports nothing, a stage running on the direct `openai` provider, and the pre-flight estimates that run before any call). Model pricing is never guessed: see `.claude/rules/application-yaml.md`.

## Production Database

The application database is at `./data/ai-summary-podcast.db`. Never query the database directly for information that is available via the application's REST API. Always use the API endpoints for production operations (generating episodes, publishing, approving, etc.). Only use direct database queries as a last resort, and always ask the user for permission before modifying the database directly.

## Frontend (Next.js Dashboard)

The frontend lives in `frontend/`. Its conventions are documented in `frontend/CLAUDE.md`, which loads automatically when working with files under that directory.

## Knowledge Bundle

`knowledge/` holds what we have measured about the models and APIs this project
depends on, why the prompt rules are shaped the way they are, and what has been
tried and rejected. It is a plain Open Knowledge Format v0.2 directory: markdown
with YAML frontmatter, no loader and no build step. Nothing in the application
reads it, and no automated process writes to it.

**Three layers.** The raw layer is the episode archive and its scripts, probe
output, score rows, reference transcripts, the git history, the OpenSpec archive
and past session transcripts; it is cited, never rewritten. The bundle is the
layer the agent owns and maintains. Read `knowledge/index.md` for its contents,
and `.claude/rules/knowledge-entries.md` for how an entry is written.

**Three operations:**

1. **Record**, within the task that produced the knowledge, before reporting it
   complete: the entry, the index that lists it, and `knowledge/log.md`. A result
   showing no difference is recorded on the same terms as one showing a
   difference.
2. **File back** an answer with standing value that was produced while answering
   a question, instead of leaving it in the conversation.
3. **Lint** the whole bundle at the end of any session that touched `knowledge/`,
   and whenever asked: contradictions, expired `stale_after`, orphans, concepts
   referenced with no entry, missing cross-references. The pass covers every entry,
   not just the ones the session changed, because a new entry is the most common way
   an old one becomes wrong. Lint removes and merges as well as adds, and rewrites
   any entry whose body has started narrating its own edit history.

**Boundary with the machine-local memory store**: what belongs to the repository
goes in `knowledge/`; what belongs to this machine and to how we work together
stays in memory.

## OpenSpec Workflow

All code changes must go through an OpenSpec change, either created before implementation (`/opsx:new`) or retroactively after implementation (`/opsx:new` covering the work done). Never implement features without a corresponding OpenSpec change.

For small changes (e.g. a one-function prompt tweak, a copy fix, a localized bug fix), it is fine to implement first and retrofit the OpenSpec change after the fact rather than creating it up front. Larger or architectural changes should still create the OpenSpec change before implementation.

When archiving an OpenSpec change (`/opsx:archive`), always update `README.md` to reflect any new or changed capabilities introduced by the change. Follow the README Structure rules in the `readme-structure` skill when making updates. After completing the archive, always ask the user to commit the changes with `/conventional-commits:cc`.
