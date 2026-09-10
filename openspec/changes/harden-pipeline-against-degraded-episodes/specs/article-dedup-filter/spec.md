## ADDED Requirements

### Requirement: Dedup filter rejects a degenerate response

A dedup response that parses cleanly can still be unusable. The prompt permits an empty
`selectedArticleIds` only for a `CONTINUATION` cluster with no new information; a `NEW` cluster
must keep its articles (all of them, or the three most comprehensive). A response naming `NEW`
clusters while selecting nothing for them has broken its own contract, and composing from what
survives yields an episode that covers a fraction of the day's material. This SHALL be detected on
every response, not only on a salvaged one.

A `NEW` cluster that selects no article SHALL be treated as invalid. When the majority of a
response's `NEW` clusters select no article, the response SHALL be rejected as degenerate and the
stage SHALL raise, so the retry applies and, failing that, the error propagates and fails the
episode. When only a minority of `NEW` clusters select nothing, those clusters SHALL be dropped and
logged at WARN, costing only their topics.

A response in which every cluster is a `CONTINUATION` that selects nothing remains valid: that is a
day with no new developments, and the pipeline skips the episode without raising.

#### Scenario: Well-formed response selecting almost nothing is rejected

- **WHEN** the dedup response parses cleanly into 34 `NEW` clusters of which 33 carry an empty
  `selectedArticleIds`
- **THEN** the response is rejected as degenerate, the retry applies, and the episode is not
  composed from the single selected article

#### Scenario: A minority of empty NEW clusters costs only those topics

- **WHEN** the dedup response parses cleanly into 30 `NEW` clusters of which 2 carry an empty
  `selectedArticleIds`
- **THEN** the 28 populated clusters are used, and a WARN records the 2 dropped topics

#### Scenario: Continuations with nothing new stay valid

- **WHEN** every cluster in the response is a `CONTINUATION` selecting no article
- **THEN** the response is accepted, no error is raised, and the pipeline skips briefing generation
  for that run

#### Scenario: Degenerate response on every attempt fails the episode

- **WHEN** every dedup attempt returns a response whose `NEW` clusters are mostly empty
- **THEN** the error propagates, the episode is marked FAILED, and no episode is composed or
  published
