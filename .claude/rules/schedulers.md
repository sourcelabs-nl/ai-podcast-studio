---
paths:
  - "src/main/kotlin/**/*Scheduler.kt"
---

# Scheduler authoring rules

A scheduler is an entry point, like a controller. It triggers work, it does not own business logic or data access.

**Do**
- Route all data access through a service method. If you need `findAll()` or a cleanup/delete, add it to the owning service and call that.
- Use Kotlin coroutines for async/background work; use `Dispatchers.IO` for I/O-bound scopes.
- Share logic with other entry points: if an API endpoint and a scheduler do the same thing, both call one service method (no copy-paste).

**Don't**
- Inject a `Repository` directly (e.g. `PodcastRepository.findAll()`, `articleRepository.deleteOld...`). Go through the service layer.
- Use `ExecutorService`, `Executors`, `Thread()`, `thread {}`, or `CompletableFuture`.
- Use `Dispatchers.Default` for I/O.

**Not a violation**
- `TaskScheduler` and the `ScheduledFuture` it returns — that is the sanctioned Spring API for dynamic rescheduling/cancellation. The concurrency rule targets thread-pool creation, not Spring-managed scheduling handles.

Full rules: `architecture` skill (A2, A3) and `kotlin-quality` skill (K7).
