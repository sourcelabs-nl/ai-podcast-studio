<!-- Implemented before this change was written; every task below is already done. -->

## 1. Register the model

- [x] 1.1 Add `deepseek/deepseek-v4.1-flash` to `app.models.openrouter` at input 0.30, output 1.20 USD per Mtok
- [x] 1.2 Verify endpoint eligibility and the routed price with a live probe under the account's provider guardrails, and record what the price refers to in a comment

## 2. Point the stages at it

- [x] 2.1 Set the `filter`, `dedup` and `compose` defaults in `application.yaml`
- [x] 2.2 Rewrite the accompanying comments, dropping the GLM-5.3 rationale
- [x] 2.3 Probe reasoning behaviour at `none` and at the compose stage's medium effort before switching
- [x] 2.4 Bring the code-level `StageDefaults` fallbacks in line with `application.yaml`
