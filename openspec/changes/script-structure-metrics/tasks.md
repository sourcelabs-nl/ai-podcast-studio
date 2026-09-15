## 1. Metrics

- [ ] 1.1 `ScriptMetrics.of(script)`: turn count, per-role turn and word counts, word share per role, expert turn length median/max/over-threshold, `[laugh]` ownership per role
- [ ] 1.2 Backchannel candidates: a short interviewer turn between two expert turns, reported with turn indices
- [ ] 1.3 Consecutive same-speaker runs, reported with turn indices and roles
- [ ] 1.4 Unit tests over hand-written scripts with known values, including a script with no expert turns and one with no turns at all

## 2. API

- [ ] 2.1 Endpoint returning the metrics for one episode
- [ ] 2.2 Endpoint returning the metrics for a range of episodes, so the archive is read over HTTP and not from the database
- [ ] 2.3 Controller validates input and delegates, holding no logic
- [ ] 2.4 Controller tests

## 3. Baseline

- [ ] 3.1 Run the metrics over the archive and report the distribution per metric
- [ ] 3.2 Record what the distribution says about the two open questions: episode length spread, and whether backchannels appear from the first episode generated with the rule

## 4. Quality gate

- [ ] 4.1 `mvn test` green
- [ ] 4.2 `/code-review --all` clean, repeating until it is
- [ ] 4.3 Restart the application
