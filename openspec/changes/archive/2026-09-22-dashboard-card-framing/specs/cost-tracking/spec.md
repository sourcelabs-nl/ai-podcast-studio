## ADDED Requirements

### Requirement: The dedup gate row names the model it ran on
The dedup gate runs on a model named in configuration rather than one resolved per
podcast, so the episode carries no column to read its name from. The episode costs
response SHALL therefore report the configured gate model in `costs.dedupGate.model`.

The name SHALL be reported only for an episode whose gate issued at least one
request. An episode that ran without the gate, or that predates the gate's cost
attribution, SHALL report a null model, because naming the configured model there
would name a model that never saw that episode.

#### Scenario: Gate that ran names its model
- **WHEN** an episode whose `dedupGateCalls` is greater than zero is returned via the API
- **THEN** `costs.dedupGate.model` is the configured dedup gate model

#### Scenario: Gate that never ran names nothing
- **WHEN** an episode whose `dedupGateCalls` is zero is returned via the API
- **THEN** `costs.dedupGate.model` is null, whatever the configuration currently names
