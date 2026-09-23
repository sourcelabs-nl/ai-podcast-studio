## Purpose

Defines the single mechanism by which the episode-generation pipeline is invoked, replacing the seven independent entry points that existed before this change with one runner taking an explicit run specification and configuration.

## ADDED Requirements

### Requirement: The pipeline is invoked only through PipelineRunner
Every route that runs the article-selection/dedup/compose pipeline SHALL do so by constructing a run specification and passing it to the pipeline runner. No route SHALL sequence pipeline stages (aggregate/score/filter, dedup, compose) directly.

A run specification SHALL declare: its purpose (one of `SCHEDULED`, `MANUAL`, `FOCUS`, `RETRY`, `RERUN`, `REGENERATE`, `RECOMPOSE`, `EXPERIMENT`, `PREVIEW`), its input (a time window, an existing episode's article set, or a focus text with a window), the pipeline stage to resume from, an optional set of configuration overrides, and the outcome to apply to the result.

#### Scenario: A route builds a run specification instead of calling stages directly
- **WHEN** any of the existing generation routes (scheduled, manual, focus, retry, re-run, regenerate, recompose, preview) is triggered
- **THEN** it constructs a run specification with the corresponding purpose and calls the pipeline runner, rather than calling the aggregate/dedup/compose stages itself

### Requirement: Four outcome types govern what happens to a run's result
A run's outcome SHALL be exactly one of:
- **Deliver**: the resulting episode is sent to TTS, published per the podcast's publication settings, and (for a regular, non-focus episode) advances the podcast's schedule and marks its articles consumed.
- **Review**: the resulting episode stops at `PENDING_REVIEW` and is not sent to TTS.
- **Sandbox**: the resulting episode is never sent to TTS, is never published regardless of auto-publish settings, does not update the podcast's `lastGeneratedAt`, and does not mark any article as consumed.
- **Transient**: the pipeline's stages run, but nothing is persisted: no episode row, no article links, no score.

#### Scenario: A sandboxed run never reaches TTS or publishing
- **WHEN** a run's outcome is `Sandbox`
- **THEN** the resulting episode has no audio generated, is not published to any target even where auto-publish is enabled, and the podcast's `lastGeneratedAt` is unchanged

#### Scenario: A transient run persists nothing
- **WHEN** a run's outcome is `Transient`
- **THEN** no episode, episode-article link, or score row exists afterward that did not exist before the run

### Requirement: Run configuration is resolved once per run from three layers
The effective configuration for a run (model per stage, reasoning effort per stage, provider sort/preferred minimum throughput, target word count, research budget, and whether the LLM cache is bypassed) SHALL be resolved once at the start of the run by layering, in order: application defaults, the podcast's own settings, and the run's own overrides (highest precedence). Every downstream component that needs one of these values (model resolution, provider routing, composition, research) SHALL receive the already-resolved configuration rather than re-reading the podcast's settings itself.

#### Scenario: A run override supersedes the podcast's own setting
- **WHEN** a run specifies a compose-stage reasoning effort override and the podcast's own setting differs
- **THEN** the run's compose stage uses the override, and the podcast's own stored setting is unchanged

#### Scenario: No override falls back to the podcast's setting
- **WHEN** a run specifies no override for a given field
- **THEN** the podcast's own setting is used, exactly as it would be without this change

### Requirement: The resolved configuration is persisted with the episode
An episode produced by a non-transient run SHALL persist the exact configuration snapshot it ran under (including any overrides applied), so that a later reader can determine what conditions produced that episode's script without re-deriving it from current podcast settings, which may since have changed.

#### Scenario: A later config change does not retroactively change what a past run reports
- **WHEN** the podcast's default compose model changes after an episode was generated
- **THEN** that episode's persisted configuration snapshot still names the model it was actually generated with

### Requirement: The epilogue runs once per non-transient outcome, per its own rules
The shared epilogue (judge, recap, show notes, sources) SHALL run once per run whose outcome is `Deliver`, `Review`, or `Sandbox`, following the epilogue's own mode/skip rules. A `Transient` run SHALL NOT invoke the epilogue, since it persists no script for the epilogue to act on.

#### Scenario: A sandboxed experiment is still judged
- **WHEN** a run has outcome `Sandbox` and purpose `EXPERIMENT`
- **THEN** the epilogue judges the resulting script exactly as it would for a `Deliver` outcome, subject to the judge mode
