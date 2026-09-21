## ADDED Requirements

### Requirement: An already-covered gate runs ahead of the dedup clustering call

`TopicDedupFilter` SHALL ask a Jev already-covered gate, before it builds its clustering prompt, which of the candidate articles are about a topic the recent episodes already covered, and SHALL exclude those candidates from the prompt.

The gate SHALL ask one `noul` question per candidate against a single shared state carrying the candidate titles and summaries alongside the covered topics, in one request. It SHALL NOT issue one request per candidate: each request would re-send the covered-topics record, which measured at $0.00605 for 60 candidates against $0.00074 for the same 60 batched, and against $0.00354 for the clustering call the gate exists to relieve.

A candidate SHALL be excluded when its answer is at or above the configured threshold, default 0.8. Measured over 60 candidates, of which 20 were drawn from the previous episode and so provably already covered, that threshold excluded all 20 and flagged the same 4 of the 40 remaining candidates that the clustering call independently treated as covered. Separation between the two groups was AUC 0.99.

The gate changes what the clustering call is asked to cluster; it does not change what that call decides about what it still sees. Clustering, per-cluster selection and `previousContext` remain the clustering call's work, none of them being a closed decision Jev can answer.

#### Scenario: An already-covered candidate never reaches the clustering prompt
- **WHEN** a candidate's already-covered answer is 0.95 and the threshold is 0.8
- **THEN** it is absent from the candidate block of the dedup prompt and is not selected for composition

#### Scenario: A fresh candidate is passed through
- **WHEN** a candidate's already-covered answer is 0.2
- **THEN** it appears in the dedup prompt exactly as it does today

#### Scenario: A candidate exactly at the threshold is excluded
- **WHEN** a candidate's already-covered answer equals the threshold
- **THEN** it is excluded, the threshold being inclusive

#### Scenario: The gate is skipped when nothing has been covered
- **WHEN** the history carries no covered topics
- **THEN** no gate request is made and every candidate reaches the clustering prompt

### Requirement: An unavailable gate leaves the stage exactly as it was

When the gate returns no answers, for any reason, `TopicDedupFilter` SHALL cluster the full candidate list and SHALL NOT fail, skip the stage, or drop any candidate. A candidate for which the gate returned no answer SHALL be treated as not covered.

The gate is an optimisation over a stage that already works. The decisions endpoint is alpha and served by one provider with no fallback, so an outage there must cost the pipeline nothing beyond the gate's own latency. Failing an episode on it would trade a documented weakness for a new one.

#### Scenario: A gate outage does not fail the episode
- **WHEN** the gate is unreachable during episode generation
- **THEN** the dedup stage clusters every candidate and the episode proceeds

#### Scenario: A partial answer set drops only what it answered for
- **WHEN** the gate answers for some candidates and not others
- **THEN** only the answered candidates at or above the threshold are excluded, and the unanswered ones are clustered

#### Scenario: A gate outage is visible
- **WHEN** the gate returns no answers
- **THEN** a warning records that the stage ran ungated

### Requirement: The gate may not end a run on its own

When the gate excludes every candidate, `TopicDedupFilter` SHALL cluster the full candidate list instead, log a warning, and let the clustering call decide. The gate SHALL NOT be able to produce an empty filter result by itself.

An empty filter result ends the run: the pipeline reads it as "every topic was already covered" and skips the episode without raising an error, which on a genuinely quiet day is correct. A gate malfunction excluding everything would be indistinguishable from that quiet day and would silently cost an episode, the same shape of failure as episode 204 composing from a single article. Deciding that nothing is left to say belongs to the clustering call, whose response is checked for degeneracy before it is acted on.

#### Scenario: A gate excluding everything is overruled
- **WHEN** the gate returns an answer at or above the threshold for every candidate
- **THEN** every candidate reaches the clustering prompt and a warning records that the gate was overruled

#### Scenario: The clustering call may still end the run
- **WHEN** the gate is overruled and the clustering call then selects no article from any cluster
- **THEN** the filter result is empty and the run is skipped, on the clustering call's evidence

### Requirement: The gate's cost and effect are accounted on the dedup stage

The gate's provider-reported cost SHALL be added to the dedup stage's reported cost, and the number of candidates it excluded SHALL be logged with the candidate count before and after.

A Jev call passes through neither `CachingChatModel` nor `CostEstimator`, so without this it would be spend the cost reporting cannot see. The exclusion count is what makes the gate's effect auditable against the clustering call that follows it: a gate that starts excluding most of the candidate set is a fault, and nothing else in the stage would reveal it.

#### Scenario: Gate cost reaches the stage total
- **WHEN** the gate reports a cost and the clustering call reports a cost
- **THEN** the dedup stage's reported cost is their sum

#### Scenario: The exclusion is logged
- **WHEN** the gate excludes 25 of 60 candidates
- **THEN** a log line records both counts

#### Scenario: An ungated run adds no cost
- **WHEN** the gate returned no answers
- **THEN** the dedup stage's reported cost is the clustering call's alone
