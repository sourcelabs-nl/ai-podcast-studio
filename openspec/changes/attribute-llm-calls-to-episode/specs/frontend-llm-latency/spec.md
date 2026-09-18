## MODIFIED Requirements

### Requirement: Latency is shown per stage against its timeout
The episode detail page SHALL provide a view showing, per pipeline stage, the p50, p90, p95 and p99
of the viewed episode's recorded request latency alongside the request timeout that stage is
configured with.

Showing the timeout next to the percentiles is what makes them actionable: a percentile on its own
says how slow requests are, not whether the ceiling above them is wrong.

#### Scenario: Stages with recorded latency
- **WHEN** the view is shown and the episode has recorded requests in a stage
- **THEN** that stage's percentiles and its configured timeout are displayed together

#### Scenario: A percentile approaching its timeout
- **WHEN** a stage's p99 is close to that stage's configured timeout
- **THEN** both values are visible together, so the proximity can be seen without arithmetic

## ADDED Requirements

### Requirement: The view is scoped to the episode being viewed
The view SHALL present the figures of the episode whose page it is on, and SHALL state that scope.

#### Scenario: Viewing the latency on an episode page
- **WHEN** the view is displayed on an episode's detail page
- **THEN** the figures shown cover only that episode's requests, and the heading says so

### Requirement: The episode's individual requests are listed
The view SHALL list the episode's individual requests beneath the per-stage percentiles, showing for
each its stage, model, start time, duration and outcome.

An episode issues on the order of a dozen requests per stage, where a percentile table compresses
away the one fact being looked for: which request was slow. The list is the answer and the
percentiles are the summary, so both are shown.

#### Scenario: An episode with recorded requests
- **WHEN** the view is shown for an episode that has recorded requests
- **THEN** each request is listed individually with its stage, model, start time, duration and
  outcome

#### Scenario: A request that failed or was served from cache
- **WHEN** a listed request failed or was served from cache
- **THEN** that is visible on its row, so it is not read as a normal request that took that long

### Requirement: An episode with no attributed requests is distinguishable
The view SHALL distinguish an episode that issued no requests from an episode generated before
requests carried an episode.

Attribution does not apply retrospectively, so every episode generated before this existed shows
nothing. Presenting that as "no requests" would read as a fact about the episode rather than about
the records.

#### Scenario: An episode generated before attribution existed
- **WHEN** the view is shown for an episode whose requests carry no attribution
- **THEN** it states that this episode predates per-episode recording, rather than reporting zero
  requests

## REMOVED Requirements

### Requirement: The window is not presented as episode data
**Reason**: The view no longer shows a cross-episode window, so there is nothing to disclaim. It now
shows the episode being viewed, and states that scope instead.
**Migration**: Replaced by "The view is scoped to the episode being viewed".
