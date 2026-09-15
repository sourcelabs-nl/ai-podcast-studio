## 0. Prerequisite

- [x] 0.1 `script-structure-metrics` implemented, and its baseline read, so it is known which questions still need a judge

## 1. Judge

- [x] 1.1 `ScriptJudge` with a prompt that returns turn anchors: promises and payoffs, humor beats with role and whether each reacts, teaser topics
- [x] 1.2 Structured output via non-streaming `.call().responseEntity(BeanOutputConverter(...))` with the injected `JsonMapper` and temperature 0.3 (SA1, SA3, SA5). `validateSchema()` is not reachable here: the project builds its models by hand in `ChatClientFactory` and every structured stage uses the converter idiom, so the judge follows it rather than introducing a second one
- [x] 1.3 Escalate the prompt per attempt so a retry never re-sends an identical prompt (SA3)
- [x] 1.4 Record `TokenUsage` and cost via `CostEstimator` (SA6)
- [x] 1.5 Add `PipelineStage.EVAL`, a `StageDefaults.eval` default and the model's pricing in `application.yaml`

## 1b. Modes

- [x] 1b.1 `JudgeMode` (`OFF`, `ADVISE`, `ENFORCE`) and an optional norm, bound from `app.eval.judge` with mode defaulting to `ADVISE` and the norm to null
- [x] 1b.2 `OFF` short-circuits before the model call, so nothing is spent and no row is written
- [x] 1b.3 `ENFORCE` with no norm configured behaves as `ADVISE` and logs once per run that the norm is absent
- [x] 1b.4 `ENFORCE` with a norm records the shortfall against it
- [x] 1b.5 Tests covering all three modes, including the norm-absent fallback
- [x] 1b.6 The generation pipeline judges each finished episode, and a judge failure never fails the episode

## 2. Derived score

- [x] 2.1 Compute deferral distance in turns and in intervening topics from the judged anchors
- [x] 2.2 Compute humor speaker balance and reaction ratio from the judged anchors
- [x] 2.3 Tests that pin the arithmetic against fixed anchor inputs, with no model involved

## 3. Persistence and API

- [x] 3.1 Flyway migration creating `episode_scores`, keyed by episode and scorer version, carrying the judge model and cost
- [x] 3.2 `EpisodeScoringService` scoring a set of episodes, skipping episodes already scored at the current version
- [x] 3.3 Controller to trigger scoring and to read scores back, validating input and delegating (no logic in the controller)
- [x] 3.4 Tests for the service and the controller

## 4. Baseline and calibration

- [ ] 4.1 Score the 166 archived scripts and report the distribution per metric
- [ ] 4.2 Calibrate: check the judge's anchors by hand on a few episodes, and record where it was wrong before any rule is changed on the strength of a number

## 5. Repetition safety

- [ ] 5.1 Add an explicit cache bypass for evaluation runs, so k repetitions produce k model calls rather than one call and k-1 cache hits
- [ ] 5.2 Record per run: prompt hash, variety selection, model, temperature, cache-hit flag, compose tools fired, resulting episode
- [ ] 5.3 A test proving two identical evaluation runs reach the model twice

## 6. Quality gate

- [x] 6.1 `mvn test` green
- [ ] 6.2 `/code-review --all` clean, repeating until it is
- [ ] 6.3 Restart the application
