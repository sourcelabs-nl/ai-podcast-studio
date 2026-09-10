## MODIFIED Requirements

### Requirement: Full-pipeline retry reselects from the episode's own window
When a retry resumes at the full pipeline, article selection SHALL use the window stored on the
episode being retried, so the retry reselects from the period the run started with rather than from
a window derived from the current state. An episode that carries no window SHALL fall back to a
window ending at the current instant.

#### Scenario: A retry selects the original period
- **WHEN** an episode that failed before article selection is retried the next morning
- **THEN** selection runs over the window stored on that episode, not over the last 24 hours

#### Scenario: A pre-migration episode still retries
- **WHEN** an episode from before windows were recorded is retried
- **THEN** the retry resolves a window ending now and proceeds
