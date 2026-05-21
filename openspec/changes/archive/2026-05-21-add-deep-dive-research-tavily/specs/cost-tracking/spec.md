## ADDED Requirements

### Requirement: Research cost fields on the episode response

Each episode SHALL track and expose research-related cost fields: `researchCalls` (integer count of `webSearch` invocations) and `researchCostCents` (USD cents, nullable when no research occurred or no pricing is configured).

#### Scenario: Episode without research has zero/null fields

- **WHEN** an episode is generated with `deepDiveEnabled=false`
- **THEN** the episode response has `researchCalls=0` and `researchCostCents` is `null` or `0`

#### Scenario: Episode with research reports counts and cost

- **WHEN** an episode is generated with 2 successful `webSearch` calls and a configured per-call price
- **THEN** the episode response has `researchCalls=2` and `researchCostCents` equals 2 × configured per-call cost in cents
