## ADDED Requirements

### Requirement: Tavily research provider entry

The model registry SHALL include a `tavily` provider entry under `app.research.tavily` exposing a per-call price (`cost-per-call-cents`) used to compute `research_cost_cents` per episode. The provider entry SHALL be loaded into `AppProperties` and surfaced through `GET /config/defaults` so the frontend can determine whether Tavily research is available.

#### Scenario: Provider entry loads

- **WHEN** the application starts with `app.research.tavily.cost-per-call-cents=0.4` configured
- **THEN** `AppProperties.research.tavily.costPerCallCents` is `0.4`

#### Scenario: Defaults endpoint exposes research provider

- **WHEN** the frontend calls `GET /config/defaults`
- **THEN** the response includes a `research` section listing `tavily` with its per-call cost
