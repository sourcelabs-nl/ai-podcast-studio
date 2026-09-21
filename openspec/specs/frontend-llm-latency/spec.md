# frontend-llm-latency Specification

## Purpose
Presents the recorded per-request LLM latency in the dashboard, next to the cost the same stages
already report, so that a request timeout can be judged against what requests actually take without
leaving the tool or calling the API by hand.

## Requirements

### Requirement: Latency is shown per stage against its timeout
The episode detail page SHALL provide a view showing, per pipeline stage, the p50, p90, p95 and p99
of recorded request latency alongside the request timeout that stage is configured with.

Showing the timeout next to the percentiles is what makes them actionable: a percentile on its own
says how slow requests are, not whether the ceiling above them is wrong.

#### Scenario: Stages with recorded latency
- **WHEN** the view is shown and stages have recorded requests
- **THEN** each stage's percentiles and its configured timeout are displayed together

#### Scenario: A percentile approaching its timeout
- **WHEN** a stage's p99 is close to that stage's configured timeout
- **THEN** both values are visible together, so the proximity can be seen without arithmetic

### Requirement: The window is not presented as episode data
The view SHALL state that it covers a rolling time window across all episodes, not the episode being
viewed.

The recorded latency carries no episode attribution, so a per-stage table on an episode's page would
otherwise be read as that episode's timings, which would be wrong in a way the reader cannot detect.

#### Scenario: Viewing the latency on an episode page
- **WHEN** the view is displayed on an episode's detail page
- **THEN** its heading states that the figures cover a window across all episodes

### Requirement: Sample counts are visible
The view SHALL show how many requests each stage's percentiles were computed from.

#### Scenario: A stage with few samples
- **WHEN** a stage's percentiles rest on a small number of requests
- **THEN** the sample count is displayed, so the figures are not read as settled

#### Scenario: A stage with no requests in the window
- **WHEN** a stage issued no qualifying requests in the window
- **THEN** the stage is shown as having no data rather than being omitted or shown with a zero latency

### Requirement: The view degrades without affecting the rest of the page
A failure to load the latency SHALL NOT prevent the rest of the episode page from rendering, and
SHALL be reported rather than shown as an absence of data.

#### Scenario: The latency request fails
- **WHEN** the latency data cannot be loaded
- **THEN** the view reports that it could not be loaded, and the episode's other tabs are unaffected

#### Scenario: A response that is not the expected shape
- **WHEN** the request succeeds but the response does not carry the expected stages
- **THEN** it is treated as a failure to load rather than rendered

#### Scenario: Latency is still loading
- **WHEN** the latency data has not arrived yet
- **THEN** the view shows that it is loading, and the episode page is already usable
