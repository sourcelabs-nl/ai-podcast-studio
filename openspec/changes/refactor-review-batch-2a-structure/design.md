# Design

## Context

See proposal.md. `SourceService` already offers `findByPodcastId(podcastId, enabled)`; the scheduler duplicates it through the repository. `.claude/rules/schedulers.md` forbids repository injection in schedulers.

## Goals / Non-Goals

**Goals:** satisfy rules A2, A4 and A7 and Spring Data JDBC rule 14 for the listed files, keeping behavior identical.

**Non-Goals:** the filtering-in-memory findings (DB3) beyond the two calls the scheduler makes; changing polling behavior.

## Decisions

- `SourceService.findEnabled()` and `findEnabledByPodcastId()` use a derived repository query (`findByEnabledTrue`, `findByPodcastIdAndEnabled(podcastId, true)`), so filtering happens in the database. The jitter write becomes `SourceService.applyStartupJitter(source, nextPollAt)` or the nearest equivalent, named after what the scheduler means by it.
- `isDue` and `effectivePollIntervalMinutes` become members of `Source` taking the properties they need as parameters, so the domain class does not depend on Spring configuration.
- Moved types keep their names and packages unless the target file lives in another package; only imports change.

## Risks / Trade-offs

- [A moved or renamed member breaks a test that constructs the scheduler] -> Update those tests in the same change and run `mvn test`.
