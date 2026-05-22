---
name: kotlin-quality
description: Kotlin code quality rules for type safety, idioms, function size, dead code, testing consistency, reuse, and concurrency. Applied to all .kt files during code review.
user_invocable: false
---

# Kotlin Quality Rules

9 rules covering code quality, idioms, and maintainability. Applied to all `.kt` files.

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

---

## Rule K7: No Raw Concurrency Primitives

All async and background work must use Kotlin coroutines. Do not use Java concurrency primitives directly, as they bypass Spring's lifecycle management, error handling, and observability.

**Violations to flag:**
- Use of `ExecutorService`, `Executors`, `ThreadPoolExecutor`, or any `java.util.concurrent` thread pool
- Use of `Thread()` or `thread {}` for async work
- Use of `CompletableFuture` for async orchestration (use coroutines instead)
- Creating unmanaged threads that bypass Spring's task executor
- Using `Dispatchers.Default` for I/O-bound work (must use `Dispatchers.IO` for HTTP requests, database calls, file I/O)

**Not a violation:**
- `Semaphore` from `kotlinx.coroutines.sync` (coroutine-aware concurrency primitive)
- `ConcurrentHashMap` or other concurrent data structures used for thread-safe state

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
```