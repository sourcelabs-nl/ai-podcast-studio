## ADDED Requirements

### Requirement: Each article appears at most once in the filtered result

The dedup filter SHALL return each article at most once, regardless of how many clusters the LLM listed it in. When an article id appears in the `selectedArticleIds` of more than one cluster, the system SHALL keep the occurrence from the first such cluster in the model's own ordering (so the article's follow-up context and topic label come from the cluster the model considered most relevant) and discard the rest.

The system SHALL log a warning when any article was selected by more than one cluster, since a response that multiplies its input signals a degenerating dedup call.

The compose-input cap SHALL additionally de-duplicate by article id before ranking and truncating, so that the composer cannot receive the same article twice even if an upstream component returns duplicates.

#### Scenario: Article selected by two clusters appears once
- **WHEN** the dedup LLM returns article 42 in both a NEW cluster "agent benchmarks" and a CONTINUATION cluster "coding agents"
- **THEN** the filtered result contains article 42 exactly once, annotated with the NEW cluster's topic, since that cluster came first in the response

#### Scenario: Duplicate selection is warned about
- **WHEN** a dedup response selects at least one article in more than one cluster
- **THEN** a warning is logged reporting how many duplicate selections were discarded

#### Scenario: Filtered result never exceeds the candidate count
- **WHEN** 68 candidates are filtered and the LLM emits 44 clusters collectively naming 356 article ids
- **THEN** the filtered result contains at most 68 articles

#### Scenario: Compose cap counts distinct articles
- **WHEN** the compose cap is 40 and the filtered result contains 9 distinct articles repeated to a length of 356
- **THEN** the composer receives 9 articles, not 40 slots filled with repeats

#### Scenario: Dedup log reports distinct counts
- **WHEN** the dedup filter completes
- **THEN** the logged "selected" count is the number of distinct articles returned

#### Scenario: Normal response is unaffected
- **WHEN** a dedup response selects each article in exactly one cluster
- **THEN** the filtered result is identical to what it would have been before this requirement, in the same order
