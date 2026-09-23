## ADDED Requirements

### Requirement: Experiment episodes are excluded from auto-publish
An episode with purpose `EXPERIMENT` SHALL NOT be auto-published, regardless of the status it reaches or which publication targets are enabled with `autoPublish` turned on. This applies unconditionally, independent of the `bypassLlmCache` evaluation-run exclusion.

#### Scenario: An experiment episode never auto-publishes
- **WHEN** an experiment run produces an episode and that episode's status would otherwise qualify it for auto-publish
- **THEN** the system does not auto-publish it to any target, even where an enabled target has `autoPublish = true`
