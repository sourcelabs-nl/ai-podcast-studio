## ADDED Requirements

### Requirement: Dedup filter behavior remains unchanged

The existing `TopicDedupFilter` behavior SHALL be unchanged: it MUST continue to receive article titles and summaries from the most recent `recapLookbackEpisodes` (default 7) episodes and label clusters as `NEW`, `CONTINUATION`, or skipped. The new `searchPastEpisodes` tool at compose time SHALL be additive — it MUST NOT replace, gate, or alter the dedup-filter decisions.

#### Scenario: Dedup filter still runs unchanged

- **WHEN** the pipeline runs for any podcast
- **THEN** `TopicDedupFilter.filter()` is called with the same inputs and produces the same outputs as before this change
