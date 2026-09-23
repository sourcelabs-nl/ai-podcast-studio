## Purpose

Defines the experiment feature: running N configuration variants x k repeats against an existing episode's article set to compare them, and the guarantee that an experiment episode never leaks into a podcast's regular listings, schedule, or publishing surfaces.

## ADDED Requirements

### Requirement: Experiments run against an existing episode's article set
`POST .../episodes/{id}/experiments` SHALL accept a source episode id, a list of variants (each a set of run configuration overrides), and a repeat count k. The system SHALL run the compose stage once per (variant, repetition) pair, each as its own `PipelineRunner` run with purpose `EXPERIMENT`, input `ArticleSet` taken from the source episode's existing linked articles, and outcome `Sandbox`.

#### Scenario: N variants x k repeats produces N*k episodes
- **WHEN** an experiment request names 2 variants and k = 3
- **THEN** 6 experiment episodes are created, each linked to the source episode's article set, each composed under its variant's overrides

### Requirement: A cost guard caps the size of an experiment
The system SHALL refuse an experiment request whose variants x repeats exceeds a configured maximum, before running any compose call.

#### Scenario: An oversized experiment is refused before any cost is incurred
- **WHEN** an experiment request's variants x repeats exceeds the configured cap
- **THEN** the request is rejected with no compose call made and no experiment episode created

### Requirement: Experiment episodes are excluded from every listing, scheduling and publishing surface
An episode with purpose `EXPERIMENT` SHALL be excluded from: episode list endpoints, the public RSS/podcast feed, schedule/window resolution (the queries determining the latest covered window and the latest generated episode for a podcast), dedup history lookups, active-episode checks (whether a podcast has a generation already in flight), and auto-publish, regardless of any target's `autoPublish` setting.

#### Scenario: An experiment episode does not appear in the episode list
- **WHEN** a client lists episodes for a podcast that has experiment episodes
- **THEN** the experiment episodes are not present in the response

#### Scenario: An experiment episode does not affect schedule resolution
- **WHEN** the scheduler determines the next window to generate for a podcast that has a more recent experiment episode than its last regular episode
- **THEN** the experiment episode is ignored and the window is resolved as if it did not exist

#### Scenario: An experiment episode is always judged
- **WHEN** an experiment run completes
- **THEN** the epilogue judges its script regardless of the podcast's normal judge-triggering conditions, subject only to the judge mode being other than `OFF`

### Requirement: A comparison endpoint reports per-variant results
A `GET` endpoint SHALL return, per variant, across its repetitions: the judge score, the cost, the duration, the number of compose calls made, the reasoning tokens used, and which provider served each request.

#### Scenario: Comparison data covers every repetition of every variant
- **WHEN** the comparison endpoint is called for a completed experiment
- **THEN** the response includes judge score, cost, duration, compose call count, reasoning tokens, and serving provider for every repetition of every variant
