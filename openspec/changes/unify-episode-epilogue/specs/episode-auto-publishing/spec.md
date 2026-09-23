## ADDED Requirements

### Requirement: Evaluation runs are excluded from auto-publish
An episode produced by an evaluation run (one composed with the LLM cache bypassed to sample a prompt variant) SHALL NOT be auto-published, regardless of the status it reaches or which publication targets are enabled with `autoPublish` turned on.

#### Scenario: A regenerated evaluation sample does not auto-publish
- **WHEN** `regenerateEpisodeAsync` is called with `bypassLlmCache = true` and the resulting episode reaches `GENERATED` status
- **THEN** the system does not auto-publish that episode to any target, even where an enabled target has `autoPublish = true`

#### Scenario: An ordinary regeneration still auto-publishes as before
- **WHEN** `regenerateEpisodeAsync` is called with `bypassLlmCache = false` and the resulting episode reaches `GENERATED` status
- **THEN** auto-publish behaves exactly as it does for any other episode reaching `GENERATED`
