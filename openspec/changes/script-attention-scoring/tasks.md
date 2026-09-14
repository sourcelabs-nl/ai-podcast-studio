## 1. Deterministic metrics

- [ ] 1.1 `ScriptMetrics.of(script)`: turn count, per-role turn and word counts, expert turn length median/max/over-threshold, `[laugh]` ownership per role
- [ ] 1.2 Unit tests over a hand-written script with known values, including a script with no expert turns

## 2. Judge

- [ ] 2.1 `ScriptJudge` with a prompt that returns turn anchors: promises and payoffs, humor beats with role and whether each reacts, teaser topics
- [ ] 2.2 Structured output via `.call().entity(...)` with `validateSchema()`, injected `JsonMapper`, temperature 0.3 (SA1, SA3, SA5)
- [ ] 2.3 Escalate the prompt per attempt so a retry never re-sends an identical prompt (SA3)
- [ ] 2.4 Record `TokenUsage` and cost via `CostEstimator` (SA6)
- [ ] 2.5 Add `PipelineStage.EVAL`, a `StageDefaults.eval` default and the model's pricing in `application.yaml`

## 3. Derived score

- [ ] 3.1 Compute deferral distance in turns and in intervening topics from the judged anchors
- [ ] 3.2 Compute humor speaker balance and reaction ratio from the judged anchors
- [ ] 3.3 Tests that pin the arithmetic against fixed anchor inputs, with no model involved

## 4. Persistence and API

- [ ] 4.1 Flyway migration creating `episode_scores`, keyed by episode and scorer version, carrying the judge model and cost
- [ ] 4.2 `EpisodeScoringService` scoring a set of episodes, skipping episodes already scored at the current version
- [ ] 4.3 Controller to trigger scoring and to read scores back, validating input and delegating (no logic in the controller)
- [ ] 4.4 Tests for the service and the controller

## 5. Baseline

- [ ] 5.1 Score the 166 archived scripts and report the distribution per metric
- [ ] 5.2 Calibrate: check the judge's anchors by hand on a few episodes, and record where it was wrong before any rule is changed on the strength of a number

## 6. Repetition safety

- [ ] 6.1 Add an explicit cache bypass for evaluation runs, so k repetitions produce k model calls rather than one call and k-1 cache hits
- [ ] 6.2 Record per run: prompt hash, variety selection, model, temperature, cache-hit flag, compose tools fired, resulting episode
- [ ] 6.3 A test proving two identical evaluation runs reach the model twice

## 7. Quality gate

- [ ] 7.1 `mvn test` green
- [ ] 7.2 `/code-review --all` clean, repeating until it is
- [ ] 7.3 Restart the application
