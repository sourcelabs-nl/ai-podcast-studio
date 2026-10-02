# Design

## Context

See proposal.md. `findSourcesBehindWindow` says which sources have content outstanding; it counts any source with a failed last poll as behind. The scheduler then waits until the deadline (the slot plus `app.episode.poll-coverage-deadline-minutes`).

## Goals / Non-Goals

**Goals:** the episode starts at the slot unless waiting can actually help.

**Non-Goals:** changing the definition of behind, the deadline, the backoff formula, or polling the source earlier than its backoff allows.

## Decisions

- **Worth waiting for means polling again before the deadline.** `Source.nextPollAt` is the last poll plus the effective interval (which grows with consecutive failures and is capped). A behind source whose next poll is after the deadline is not waited for; one that never polled has no next poll time and is waited for, as it is due at once.
- **The decision stays in the scheduler.** It reads `nextPollAt` and the configured backoff cap; `findSourcesBehindWindow` is untouched, so its contract and tests stay.
- **No retry is forced.** Overriding the backoff to poll a failing host sooner would hammer a host that is already refusing; the episode simply proceeds without it and says so at WARN.
- **A circuit-broken host** is not modelled: if a due source's host is held by the breaker, the scheduler still waits as before. That keeps the change small and the wait bounded by the same deadline.

## Risks / Trade-offs

- [An episode starts without a source that would have recovered right after the backoff] -> The next episode's window picks up its content, as it does today after the deadline.
