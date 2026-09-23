---
name: kotlin-quality
description: Kotlin code quality rules for type safety, idioms, function size, dead code, testing consistency, reuse, and concurrency. Applied to all .kt files during code review.
user_invocable: false
---

# Kotlin Quality Rules

11 rules covering code quality, idioms, and maintainability. Applied to all `.kt` files.

---

## Rule K1: Type-Safe Constants Over Hardcoded Strings

Domain values that represent a fixed set of states, categories, or types must be defined as enums, not hardcoded strings. Scattered string literals are easy to misspell, impossible for the compiler to validate, and make refactoring error-prone.

**Violations to flag:**
- Status values compared or assigned as string literals (e.g., `"ACTIVE"`, `"INACTIVE"`, `"PENDING"`)
- Category or type keys used as string literals across multiple files
- Any fixed set of values used in `when` expressions or `if` chains that could be an enum
- New string constants introduced for values that already have an enum or should have one

---

## Rule K2: Testing Framework Consistency

Tests should use a single mocking framework consistently. This project uses MockK and `@MockkBean`.

**Violations to flag:**
- Mixing mocking frameworks (e.g., Mockito imports in this MockK project)
- Using `@MockBean` instead of `@MockkBean`

---

## Rule K3: Code Reuse and Consistency

Similar operations across different domain areas should follow the same structural patterns. Avoid reimplementing logic that already exists in a service.

**Violations to flag:**
- Reimplementing logic that an existing service method already provides (e.g., lookup + validation + save)
- Copy-pasted blocks across controllers or services with minor variations
- Inconsistent patterns for the same operation across different domain packages
- Utility methods duplicated across packages instead of extracted to a shared location

---

## Rule K4: Dead Code

Public functions, classes, or constants with zero callers are dead code and should be removed. This keeps the codebase clean and reduces confusion.

**Violations to flag:**
- Public functions in services, repositories, or utility classes with no callers
- Unused data classes, enums, or constants
- Unused imports (though ktlint catches most of these)

**Exceptions (do not flag):**
- Controller methods (called by the framework via HTTP)
- `@Scheduled` methods (called by the scheduler)
- Interface declarations and their implementations
- `@Bean` factory methods

---

## Rule K5: Idiomatic Kotlin: Property vs Function Extensions

Use property extensions for derived characteristics (no arguments, no side effects). Use function extensions when arguments are required or an action is performed.

**Violations to flag:**
- Extension functions with no parameters that return a derived value (should be a property)
- Extension properties that perform side effects or expensive computation (should be a function)
- Extension properties that take action rather than describe a characteristic

**Correct patterns:**
```kotlin
// Property: derived characteristic, no arguments
val Int.isEven: Boolean get() = this % 2 == 0

// Function: takes arguments, performs action
fun Int.coerceTo(range: IntRange): Int = coerceIn(range)
```

---

## Rule K6: Function Size: Single Responsibility

Functions should do one thing and be small enough to understand at a glance. Functions exceeding 50 lines of code are a sign that the function has multiple responsibilities and should be split into smaller, focused functions.

**Important:** Only flag functions that genuinely mix multiple concerns. A long function that does one thing well (e.g., a data access method with a large SQL query and row mapping) is acceptable. The goal is separation of concerns, not arbitrary line-count compliance. Do not suggest extracting SQL to a companion constant or splitting a function purely because it exceeds 50 lines.

**Violations to flag:**
- Functions that mix multiple concerns (e.g., validation, mapping, persistence, and notification in one method) AND exceed 50 lines
- Long `when` or `if/else` blocks that could be extracted into separate functions
- Deeply nested logic (3+ levels of indentation) that could be flattened by extracting helpers

**Do NOT flag:**
- Data access methods whose length comes from a large SQL query (single responsibility: data retrieval)
- Functions that are long but cohesive (all lines serve the same concern)

**Correct pattern:**
```kotlin
// Bad: one large function doing everything
fun processEpisode(request: EpisodeRequest): Episode {
    // 20 lines of validation
    // 15 lines of mapping
    // 10 lines of persistence
    // 10 lines of notification
}

// Good: small focused functions
fun processEpisode(request: EpisodeRequest): Episode {
    val validated = validate(request)
    val episode = mapToDomain(validated)
    val saved = persist(episode)
    notify(saved)
    return saved
}
```

---

## Rule K7: No Raw Concurrency Primitives

All async and background work must use Kotlin coroutines. Do not use Java concurrency primitives directly, as they bypass Spring's lifecycle management, error handling, and observability.

**Violations to flag:**
- Use of `ExecutorService`, `Executors`, `ThreadPoolExecutor`, or any `java.util.concurrent` thread pool
- Use of `Thread()` or `thread {}` for async work
- Use of `CompletableFuture` for async orchestration (use coroutines instead)
- Creating unmanaged threads that bypass Spring's task executor
- Using `Dispatchers.Default` for I/O-bound work (must use `Dispatchers.IO` for HTTP requests, database calls, file I/O)
- **`Thread.sleep(...)` inside a `suspend` function** — it blocks the underlying thread instead of suspending cooperatively. Use `delay(...)` (from `kotlinx.coroutines`) instead. If the surrounding function is not yet `suspend`, prefer making it (and its callers) `suspend` so a cooperative `delay` can be used, rather than reaching for `Thread.sleep`.

**Not a violation:**
- `Semaphore` from `kotlinx.coroutines.sync` (coroutine-aware concurrency primitive)
- `ConcurrentHashMap` or other concurrent data structures used for thread-safe state
- `TaskScheduler` and the `java.util.concurrent.ScheduledFuture` it returns. `TaskScheduler` is Spring's sanctioned abstraction for dynamic (re)scheduling and cancellation; `ScheduledFuture` is its return type, retained purely as a cancellation handle. This rule targets thread-pool creation and unmanaged async work, not retention of a Spring-managed scheduling handle.
- `runBlocking { ... }` used as a deliberate bridge from a non-`suspend` context into `suspend` code (e.g. a non-`suspend` interface override that must call a `suspend` function). Bridging is fine; reaching for `Thread.sleep` to avoid `suspend` is not.

**Testing suspend functions:**
- Drive `suspend` functions under test with `runTest { ... }` (from `kotlinx.coroutines.test`), not `runBlocking { ... }`. `runTest` uses virtual time, so `delay(...)` calls inside the code under test are skipped instead of actually waiting — keeping tests fast and deterministic.
- With MockK, stub and verify `suspend` functions using `coEvery { ... }` / `coVerify { ... }` (never plain `every`/`verify`, which do not compile for suspend functions).

---

## Rule K8: Unsupported Interface Overrides Must Throw

When an interface requires implementing a method that does not apply to the concrete class, the override must throw `UnsupportedOperationException` with a message pointing to the correct alternative. It must never return a plausible but incorrect result (e.g., a dummy value, random UUID, empty object), because a silent wrong answer is worse than a loud failure.

**Violations to flag:**
- Interface overrides that return fabricated/dummy values as placeholders for "not applicable"
- Interface overrides with comments like "will be set later", "not used", or "placeholder" that still return a value
- Interface overrides that silently ignore the call and return a no-op result when the caller would expect meaningful behavior

**Not a violation:**
- Overrides that throw `UnsupportedOperationException` with a descriptive message
- Overrides that delegate to a more specific method
- Overrides that genuinely return valid default values by design

**Correct pattern:**
```kotlin
// Bad: returns silently incorrect result
override fun toDomain(from: FormDto): Domain =
    Domain(id = null, userId = UUID.randomUUID()) // "Will be set in service"

// Good: fails explicitly, points to the correct alternative
override fun toDomain(from: FormDto): Domain =
    throw UnsupportedOperationException("Use toDomain(form, userId) instead")
```

---

## Rule K9: Multi-Dollar String Interpolation

Use Kotlin's multi-dollar string interpolation (`$$"..."`) when a string contains literal `$` characters that should not be interpreted as Kotlin string templates. This avoids the need for backslash escaping (`\$`) and improves readability.

With `$$"..."`, a single `$` is treated as a literal character. Two consecutive dollar signs (`$$`) are required to trigger Kotlin interpolation.

**Violations to flag:**
- Strings using `\$` to escape dollar signs when `$$"..."` would be cleaner
- Spring `@Value` annotations using `"\${...}"` instead of `$$"${...}"`

**Correct pattern:**
```kotlin
// Bad: backslash escaping is noisy
@Value("\${app.name}")
private val appName: String

// Good: multi-dollar string, $ is literal
@Value($$"${app.name}")
private val appName: String

// Good: nested placeholders are also clean
@Value($$"${app.base-dir:${java.io.tmpdir}/uploads}")
private val baseDir: String

// Good: Kotlin interpolation uses $$ when needed
val json = $$"""{"price": "$${price}", "currency": "$"}"""
```
---

## Rule K10: Cancellation Is Not a Failure

`CancellationException` is how coroutines unwind, not an error the code was written to handle. A broad `catch (e: Exception)` in a `suspend` function catches it, so any such block that records an error, marks a row failed, or swallows the exception both lies about what happened and breaks structured concurrency: the coroutine keeps running after it was cancelled.

Catch `CancellationException` explicitly before the general catch and rethrow it without recording anything. A cancelled operation also proves nothing about whether its external effect landed, so leave the record in the state that says "unknown" (a pending claim, or untouched) rather than asserting a failure.

**Violations to flag:**
- A `catch (e: Exception)` in a `suspend` function that persists a failure state, with no `CancellationException` branch ahead of it
- A guarded side effect (`try { ... } catch (e: Exception) { log.warn(...) }`) inside a `suspend` function that swallows cancellation
- `catch (e: Throwable)` around suspending work
- Storing a cancellation's message as an error reason (`"MonoCoroutine was cancelled"` in an `error_message` column is the symptom)

**Not a violation:**
- A `catch (e: Exception)` that only logs and rethrows
- A non-`suspend` function with no coroutine in its call path
- Catching `CancellationException` to run cleanup, provided it is rethrown

---

## Rule K11: The Third Copy Becomes a Shared Component

[Rule K3](#rule-k3-code-reuse-and-consistency) says not to copy-paste. This rule says what to do once it has already happened, because "avoid duplication" on its own has never stopped a second copy: the second one is always cheap and always defensible.

**The trigger is mechanical: at the third occurrence of the same shape, extract it.** Not "consider extracting", not "when it gets painful". Two copies can be a coincidence; three is a pattern that will keep growing, and every later copy inherits whatever was wrong with the first.

**A deferral comment is a debt marker with a due date, and the due date is binding.** A file that says *"the copy is deliberate at this size; should another case appear, extract a generic version instead of copying this file again"* has pre-authorised the refactor. Copying that file, comment and all, is not following the note, it is overruling it. When you find such a comment on a file you are about to duplicate, the extraction **is** the task.

**How to split it: share the mechanism, keep the vocabulary.**
- **Shared:** the plumbing nobody's feature owns: registries, lifecycle, fan-out, retry, eviction, locking. This is where drift does real damage, because each copy fixes a different subset of the bugs.
- **Per feature:** what the thing *is*: its domain names, its result payload, its wire contract. Forcing those into one type produces a shared component with a boolean parameter for every caller, which is worse than the duplication.
- Identical constants across every copy (the same three enum values, the same three stage codes) are mechanism, not vocabulary. Share them.
- Keep each feature's public method names and parameter names where callers already use named arguments, or absorb the rename deliberately: the compiler will find every call site, so a rename is safe, but it must be a decision rather than a side effect.

**Violations to flag:**
- A third file with the same structural shape as two existing ones (same fields, same method set, differing only in domain names and payload types)
- Copying a file that carries a comment instructing the next duplication to be extracted instead
- Duplicated identical enums or constant sets across feature packages
- Two copies of the same mechanism that have drifted in their bug fixes or edge-case handling: flag even at two copies, because the drift is the bug

**Not a violation:**
- Two similar-looking classes whose behaviour genuinely differs beyond names (an accumulating stream and a snapshot poller share a registry, not a lifecycle)
- Per-feature domain records, DTO mappers, or wire payload shapes that happen to have similar fields but are separate API contracts
