# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Session Startup

When starting a new session, read `llms.txt` in the project root. It contains links to the latest documentation for the core technologies (Spring Boot, Spring AI, Kotlin). Use these links to look up API usage and syntax when needed during implementation.

## Project Overview

Kotlin/Spring Boot application. See `README.md` for the full project description, architecture, prerequisites, and setup instructions.

## Running the Application

Use the provided scripts to start and stop the application:

- **Start:** `./start.sh` — runs the app in the background, logs to `app.log`, PID stored in `.app.pid`
- **Stop:** `./stop.sh` — gracefully stops the app (force-kills after 10s timeout)

Required environment variable (managed via direnv `.envrc`): `APP_ENCRYPTION_MASTER_KEY`. All provider credentials are managed via the UI/API.

## Testing

Use **MockK** (not Mockito) for all Kotlin tests. For Spring integration tests, use `@MockkBean` from the `springmockk` library (`com.ninja-squad:springmockk`) to inject mocks into the Spring context.

**Never leave the project in a broken state.** Every commit must compile and all tests must pass. When a code change breaks existing tests, fix those tests as part of the same change. Run `mvn test` before considering any change complete. If constructor signatures change, update all test files that instantiate the class.

## Code Review Loop

After fixing code review violations, always re-run the code reviewer (`/code-review --all`) to verify the fixes didn't introduce new violations and to catch issues that become visible after the first layer is cleaned up. Repeat until the review is clean. Fixes themselves can introduce new violations (e.g., moving logic to a service may reveal a missing `@Transactional`, or duplicating code during extraction).

## Architecture Guidelines

Controllers validate input, delegate to services, and map responses — no business logic. Never duplicate logic that already exists in a service. For the full set of architectural review rules (controller hygiene, service layer, Spring Data JDBC, database consistency, testing, Jackson 3.x), see the `code-review` skill or run `/code-review`.

**Concurrency:** Use Kotlin coroutines for async/background work — never use `ExecutorService` or `java.util.concurrent` thread pools directly. Use `Dispatchers.IO` for I/O-bound coroutine scopes (HTTP requests, database calls, file I/O) — never `Dispatchers.Default`, which is sized to CPU cores and meant for computation only.

**Transactions:** Any function that performs multiple writes across tables (or multiple writes that must be atomic) must be annotated with `@Transactional`. Remember that `@Transactional` only works on public methods called through the Spring proxy (not on private methods or internal self-calls).

**Jackson:** Configure Jackson features via Spring Boot properties (`spring.jackson.*` in `application.yaml`), not programmatically. Inject the Spring-managed `JsonMapper` bean when custom mapper configuration is needed (e.g., for `BeanOutputConverter` in Spring AI). See the `spring-boot` skill (Rule SB6) for details.

**Parameter objects:** When a function's parameter list grows long (more than 4-5 params), and the parameters are functionally related, wrap them in a Kotlin `data class` instead of adding more positional arguments. Long positional call sites are fragile under change and hurt readability. Concrete example: `InworldApiClient.synthesizeSpeech(...)` takes mandatory request identity (userId, voiceId, text, modelId) plus an `InworldSynthesisOptions(speed, temperature, deliveryMode)` data class for optional knobs — extending the options doesn't ripple through every caller. Apply this anywhere optional/related fields cluster (TTS settings, LLM request params, search filters, etc.).

**Post-implementation check:** After every code change, validate that the architecture guidelines are respected — especially controller hygiene (no business logic, no direct repository access) and proper service layer delegation. Fix violations before considering the change complete.

## Code Navigation (LSP)

Prefer LSP over Grep/Glob for semantic navigation on `.kt` and `.ts`/`.tsx` files:

- **Use LSP for:** finding definitions (`goToDefinition`), references (`findReferences`), type info (`hover`), and file structure (`documentSymbol`).
- **Kotlin LSP limitations:** `goToImplementation`, `incomingCalls`, and `outgoingCalls` are not supported. Use `findReferences` as a fallback for these.
- **Use Grep/Glob for:** text-based searches (log messages, config keys, string literals), cross-codebase pattern matching, and finding files by name or path.

## Application Restart After Changes

Whenever code changes are made to the application, always restart it (`./stop.sh` then `./start.sh`) before testing or using the new feature. Never attempt to exercise a new or modified feature against a running instance that was built from old code.

## External API Integration

When adding or modifying calls to external APIs (Inworld, ElevenLabs, OpenAI, etc.), always verify the request payload against the actual API documentation before implementing. Proto/gRPC-based APIs often use string enums (e.g., `"ON"` / `"OFF"`) rather than booleans — do not assume field types. After implementing an external API change, test it against the live API before considering the task complete.

OpenRouter reports its own cost per call, and the pipeline uses that provider-reported value wherever it is present, so the configured rates only matter as a fallback (a call that reports nothing, a stage running on the direct `openai` provider, and the pre-flight estimates that run before any call). When adding or updating model pricing in `application.yaml` (e.g., `input-cost-per-mtok`, `output-cost-per-mtok`), still verify the pricing on the provider's website (e.g., https://openrouter.ai/{provider}/{model}/pricing) before setting values. Do not guess or use training data for pricing, it changes frequently.

## Production Database

The application database is at `./data/ai-summary-podcast.db`. Never query the database directly for information that is available via the application's REST API. Always use the API endpoints for production operations (generating episodes, publishing, approving, etc.). Only use direct database queries as a last resort, and always ask the user for permission before modifying the database directly.

## Frontend (Next.js Dashboard)

The frontend lives in `frontend/`. Its conventions are documented in `frontend/CLAUDE.md`, which loads automatically when working with files under that directory.

## OpenSpec Workflow

All code changes must go through an OpenSpec change — either created before implementation (`/opsx:new`) or retroactively after implementation (`/opsx:new` covering the work done). Never implement features without a corresponding OpenSpec change.

For small changes (e.g. a one-function prompt tweak, a copy fix, a localized bug fix), it is fine to implement first and retrofit the OpenSpec change after the fact rather than creating it up front. Larger or architectural changes should still create the OpenSpec change before implementation.

When archiving an OpenSpec change (`/opsx:archive`), always update `README.md` to reflect any new or changed capabilities introduced by the change. Follow the README Structure rules in the `readme-structure` skill when making updates. After completing the archive, always ask the user to commit the changes with `/conventional-commits:cc`.
