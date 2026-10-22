## ADDED Requirements

### Requirement: Relevance score reflects listener interest

The scoring prompt SHALL ask for `relevanceScore` on topic fit and listener interest together, with a rubric: 9-10 a development people in the field will talk about, 7-8 on topic with a concrete consequence a listener could act on or retell, 5-6 on topic but incremental or narrow, 3-4 only loosely related, 0-2 irrelevant. The prompt SHALL state that a release of a model, agent or developer product that people can use scores at least 7, and that a research paper whose main contribution is a framework, harness, method or benchmark with improvements over earlier work scores at most 6. A paper SHALL reach 7 or higher only when it reports a measured finding that is surprising or broadly applicable.

#### Scenario: Prompt scores topic fit and listener interest together
- **WHEN** the scoring prompt is built
- **THEN** it contains the 0-10 rubric and states that fit alone is not enough for a high score

#### Scenario: Releases are floored and incremental papers capped
- **WHEN** the scoring prompt is built
- **THEN** it floors usable releases at 7 and caps framework, harness, method and benchmark papers at 6
