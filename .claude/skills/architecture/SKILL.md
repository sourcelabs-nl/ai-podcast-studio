---
name: architecture
description: Backend architecture rules for service layer boundaries, repository access, and shared operations. Applied to all .kt files during code review.
user_invocable: false
---

# Architecture Rules

8 rules enforcing service layer boundaries, data flow, exception design, and domain logic placement. Applied to all `.kt` files.

---

## Rule A1: Service Layer Enforcement

Controllers must not contain business logic. They validate input, delegate to service classes, and map responses.

**Violations to flag:**
- Controller methods that call repository methods directly when a service method exists for the same operation
- Controller methods that orchestrate multiple service calls with conditional logic between them
- Controller methods that construct domain objects or transform data beyond simple DTO mapping
- Any logic in a controller that duplicates what a service method already does

**Correct pattern:**
```kotlin
// Good: controller delegates to service
@PostMapping
fun create(@RequestBody request: CreateRequest): ResponseEntity<Response> {
    val result = myService.create(request.toCommand())
    return ResponseEntity.ok(result.toResponse())
}

// Bad: controller contains business logic
@PostMapping
fun create(@RequestBody request: CreateRequest): ResponseEntity<Response> {
    val existing = repository.findByName(request.name)
    if (existing != null) throw ConflictException()
    val entity = Entity(name = request.name)
    val saved = repository.save(entity)
    return ResponseEntity.ok(saved.toResponse())
}
```

---

## Rule A2: Repository Access

Controllers and schedulers should not inject repositories directly when a service method exists for the same operation. All data access should flow through the service layer.

**Violations to flag:**
- A controller or scheduler injecting a `Repository` when a corresponding service exists
- Direct repository calls that bypass validation or side effects present in the service method

---

## Rule A3: Single Code Path for Shared Operations

When the same operation can be triggered from multiple entry points (API endpoint, scheduler, CLI), the shared logic must live in a single service method. All entry points must call that method.

**Violations to flag:**
- Two or more places (e.g., controller + scheduler) implementing the same operation with duplicated logic
- A new entry point that reimplements steps already present in an existing service method
- Copy-pasted sequences of repository/service calls across controllers or schedulers

---

## Rule A4: Domain Logic Belongs in Domain Classes

Pure domain logic (calculations, derivations, mappings that depend only on domain data) must live in domain classes, enums, or companion objects, not in service classes. Services orchestrate (fetch, validate, persist, coordinate); domain objects encapsulate behavior and rules.

**Violations to flag:**
- A service method that computes or derives a value purely from domain data (e.g., resolving a date from an enum value, calculating a total from line items) when that logic could be a method or function on the domain class itself
- `when` expressions over domain enums inside a service that map enum values to domain results (these belong on the enum or a domain companion object)
- Private helper methods in a service that take only domain parameters and return domain results without touching repositories or external services

**Correct pattern:**
```kotlin
// Good: domain logic lives in the domain enum
enum class EpisodeStyle {
    MONOLOGUE,
    DIALOGUE,
    INTERVIEW;

    fun requiresMultipleSpeakers(): Boolean = when (this) {
        MONOLOGUE -> false
        DIALOGUE, INTERVIEW -> true
    }
}

// Bad: domain logic lives in the service
class EpisodeService {
    private fun requiresMultipleSpeakers(style: EpisodeStyle): Boolean = when (style) {
        EpisodeStyle.MONOLOGUE -> false
        EpisodeStyle.DIALOGUE, EpisodeStyle.INTERVIEW -> true
    }
}
```

---

## Rule A5: No HTTP-Semantics in Services

Service classes must not throw exceptions that reference HTTP concepts (`BadRequestException`, `ResponseStatusException`, etc.). Services express domain validation failures using domain-specific exceptions. The `@ControllerAdvice` or exception handler is responsible for translating domain exceptions to HTTP status codes.

**Violations to flag:**
- Service methods throwing `BadRequestException`, `ResponseStatusException`, or any exception whose name contains HTTP status concepts (e.g., `NotFound`, `Forbidden`, `Unauthorized`)
- Service methods constructing `ResponseEntity` or referencing `HttpStatus`

**Not a violation:**
- Controllers throwing `BadRequestException` for input validation before delegating to services
- Services throwing domain exceptions like `IllegalArgumentException`, `IllegalStateException`, or custom domain exceptions

---

## Rule A6: Named Data Classes Over Generic Tuples

When a method returns multiple values, use a named data class instead of `Pair`, `Triple`, or other generic tuple types. Named data classes make the return type self-documenting and the properties readable at call sites.

**Violations to flag:**
- Public or internal methods returning `Pair<...>` or `Triple<...>` where the tuple components represent domain concepts
- Methods returning generic tuples that are destructured at multiple call sites

**Not a violation:**
- Private helper methods with localized usage where the tuple meaning is obvious from context
- Inline `let`/`run` blocks that produce temporary pairs for map construction

**Correct pattern:**
```kotlin
// Good: named data class
data class LinkedArticlesResult(
    val articles: List<Article>,
    val topicLabels: List<String>,
    val articleTopics: Map<Long, String>
)
fun findLinkedArticles(episodeId: Long): LinkedArticlesResult

// Bad: generic triple
fun findLinkedArticles(episodeId: Long): Triple<List<Article>, List<String>, Map<Long, String>>
```

---

## Rule A7: Controller and Service File Hygiene

Controllers and services must be clean and concise. They must not contain data classes, enums, request/response DTOs, or mapper extension functions. These must live in dedicated files per domain package.

**Required file structure per package:**
- `{Domain}Dtos.kt` -- request and response data classes
- `{Domain}Mappers.kt` -- extension functions that convert entities to DTOs, plus update helpers (only when mappers exist)
- `{Domain}Types.kt` -- service-layer result types, enums, and value objects that are not HTTP DTOs (only when such types exist)

**Violations to flag:**
- A data class defined inside a controller or service file (nested or top-level in the same file)
- A `toResponse()` or similar mapping extension function defined as a private member of a controller
- Helper functions (e.g., `orKeep`, `toLlmModelOverrides`) defined in a controller file
- A private data class inside a controller that is duplicated across multiple controllers

**Not a violation:**
- Companion objects with constants in service classes (e.g., `PROVIDER_DEFAULT_URLS`) -- these are configuration, not data classes
- Inline anonymous objects or map literals used for one-off JSON responses
- Data classes in dedicated `*Dtos.kt`, `*Mappers.kt`, or `*Types.kt` files

---

## Rule A8: Multi-Step Workflows Live on the Server, Not in the Client

A workflow that involves more than one backend call with logic between the steps -- conditional branching, retrying, polling, or arithmetic that decides the next call -- must be implemented as a single server operation behind one endpoint. The web/frontend client must call that one endpoint and never orchestrate the workflow itself by chaining backend calls. The server has the authoritative data (live quotas, durations, DB state) and is the only place that can make these decisions correctly and atomically; a client that stitches calls together duplicates business logic, races against stale data, and cannot be reused by other entry points (schedulers, other clients).

A single point of user **consent** (e.g. confirming a destructive action) may stay in the client, but the decision of *what* to do (which records, how many, with what retries) is computed server-side and the client only forwards the confirmation.

**Violations to flag:**
- A frontend component performing a sequence like "POST publish -> on quota error DELETE oldest track -> retry POST publish on a timer" instead of calling one server endpoint that does it all
- Client code containing retry/backoff loops, polling, or "how much to free / how many to delete" arithmetic against a backend resource
- The same multi-step backend sequence implemented in more than one client, or in a client when a scheduler needs it too (belongs in a shared service method -- see Rule A3)
- A controller exposing only low-level primitives (`publish`, `deleteTrack`, `getQuota`) that force the client to assemble the actual operation

**Correct pattern:**
- Server: one endpoint / service method encapsulates the full workflow (compute plan, mutate, retry, return result). Reused by every entry point.
- Client: calls the single endpoint; on a "needs confirmation" response, shows the server-computed plan and, on consent, calls the one follow-up endpoint that performs it.
---

## Rule A9: Failure Handling Must Not Overwrite What Succeeded

A `catch` block must not persist state captured before the operation it guards, and must not span steps that cannot undo each other. When a `try` covers an external effect (an upload, a remote call, a payment) plus the bookkeeping that follows it, a failure in the bookkeeping runs a catch written for the effect: it saves a pre-effect snapshot, discarding the identifiers the effect produced, and the record then denies something that really happened. Nothing in the application can recover from that, because every later decision reads the record rather than the external system.

Guard the effect on its own. Persist its result immediately after it returns. Run everything that follows outside that block, each step guarded individually, and let a failure there be logged rather than allowed to revoke the result.

**Violations to flag:**
- A `catch` that saves an entity variable captured before the `try`, when the body reassigns or re-saves that entity (the catch silently reverts the successful write)
- A single `try` spanning an external side effect and follow-up work (cache invalidation, feed export, notification, playlist rebuild) where the catch records failure for the whole thing
- A failure path that clears an external identifier (`externalId = null`) without having removed the external resource
- Follow-up work that can flip a completed operation's status to a failed one

**Not a violation:**
- A single `try` around steps that all belong to the same all-or-nothing write, especially under `@Transactional`
- Logging and rethrowing without persisting anything
- A catch that saves a failure state built from the *current* entity rather than a stale snapshot
