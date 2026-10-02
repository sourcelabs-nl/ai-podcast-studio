# Proposal

## Why

The code review of 2026-10-01 found structure violations that change no behavior: a scheduler that reads and writes through a repository next to an existing service, domain types declared inside controller and service files, `Optional` unwrapping where Kotlin nullability is the convention, and polling-due logic that depends only on `Source` but lives in the scheduler.

## What Changes

- `SourcePollingScheduler` stops injecting `SourceRepository`: enabled-source lookups and the startup-jitter save go through `SourceService`.
- `SourcePollingScheduler.isDue` and `effectivePollIntervalMinutes` move onto the `Source` domain class.
- `ResearchTestResult` and `GenerationStatsRetry` move out of their controller and service files into the package's DTO or types file.
- `findById(...).orElse(null)` in `FtpPublisher`, `SseEventBroadcaster` and `StaticFeedExporter` becomes `findByIdOrNull(...)`.

No API, schema or observable behavior changes.

## Capabilities

### New Capabilities

### Modified Capabilities

## Impact

- `scheduler/SourcePollingScheduler.kt`, `source/SourceService.kt`, `store/Source.kt` (or wherever `Source` lives).
- `research/ResearchTestController.kt`, `research/ResearchDtos.kt`, `llm/GenerationStatsService.kt`, and a types file in `llm/`.
- `FtpPublisher.kt`, `SseEventBroadcaster.kt`, `StaticFeedExporter.kt`.
- Existing tests are updated where they construct the scheduler or call the moved members.
