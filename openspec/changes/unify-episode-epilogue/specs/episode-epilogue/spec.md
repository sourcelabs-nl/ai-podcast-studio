## Purpose

Defines the single set of steps that runs once a route has produced or rewritten an episode's script text, so that judging and recap/show-notes/sources generation happen the same way regardless of which route produced the script.

## ADDED Requirements

### Requirement: Every script-producing or script-rewriting route runs the epilogue
Any route that finalizes a new script for an episode, or replaces an existing episode's script with a rewritten one, SHALL run the epilogue exactly once for that script: it SHALL attempt to judge the script (subject to the judge mode) and SHALL attempt to generate/regenerate the recap, show notes and sources for the episode.

The epilogue SHALL run regardless of whether the episode stops at `PENDING_REVIEW`, proceeds to TTS, or (for a focus episode) never leaves review, because the script itself is complete and judgeable in every one of those cases.

#### Scenario: A rewritten focus episode is judged
- **WHEN** a reviewer submits feedback and the focus episode's script is recomposed
- **THEN** the epilogue judges the new script (subject to the judge mode) in addition to regenerating its recap

#### Scenario: A retried episode is judged
- **WHEN** a retry recomposes an episode's script from `ResumePoint.COMPOSE` or `ResumePoint.FULL_PIPELINE`
- **THEN** the epilogue judges the new script (subject to the judge mode) in addition to generating its recap

#### Scenario: An ordinary generation keeps its existing behavior
- **WHEN** the scheduled or manual generation pipeline produces an episode's script for the first time
- **THEN** the epilogue judges it and generates its recap, exactly as the pipeline already does today

### Requirement: The judge is invoked in the background and never blocks delivery
The epilogue SHALL launch the judge asynchronously so that a slow or failing judge call cannot delay or fail the route it is attached to. A judge call that throws SHALL be logged and SHALL NOT change the episode's status, script, or any other stored field.

#### Scenario: Judge failure does not fail the route
- **WHEN** the judge call throws an exception during the epilogue
- **THEN** the route that triggered the epilogue completes normally and the episode is left exactly as it would have been without the judge

### Requirement: The epilogue respects the configured judge mode
When the judge mode is `OFF`, the epilogue SHALL make no judge model call. When the judge mode is `ADVISE` or `ENFORCE`, the epilogue SHALL judge the script per the existing judge-mode behavior.

#### Scenario: Judge mode OFF makes no call from any route
- **WHEN** the judge mode is `OFF` and any route runs the epilogue
- **THEN** no judge model call is made and no score row is written
