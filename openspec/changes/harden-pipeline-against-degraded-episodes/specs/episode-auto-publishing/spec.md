## ADDED Requirements

### Requirement: Auto-publish requires a minimum article count

Auto-publish is the last point at which a degraded episode can be stopped before listeners see it.
An episode built from almost no articles is the visible symptom of an upstream failure, and
publishing it automatically turns a recoverable internal fault into a public one.

The system SHALL NOT auto-publish an episode whose linked article count is below
`app.publishing.min-articles`. Such an episode SHALL remain in `GENERATED` status with no
publication attempted, and the skip SHALL be logged at WARN naming the episode and its article
count, so it can be reviewed and published manually. This floor SHALL apply to automatic
publishing only: a manual publish of the same episode SHALL still succeed, leaving the decision
with the user.

#### Scenario: Episode with too few articles is not auto-published

- **WHEN** an episode reaches `GENERATED` with 1 linked article, `app.publishing.min-articles` is 5,
  and the podcast has an enabled `autoPublish` target
- **THEN** no publication is attempted, the episode stays `GENERATED`, and a WARN records the skip
  with the article count

#### Scenario: Episode at the floor is auto-published

- **WHEN** an episode reaches `GENERATED` with a linked article count at or above
  `app.publishing.min-articles`
- **THEN** auto-publish proceeds to every enabled `autoPublish` target as usual

#### Scenario: A skipped episode can still be published manually

- **WHEN** an episode was not auto-published because it fell below the floor and the user publishes
  it from the dashboard
- **THEN** the publish succeeds, the floor applying to automatic publishing only
