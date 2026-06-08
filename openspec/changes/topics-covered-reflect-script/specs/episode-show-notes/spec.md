## ADDED Requirements

### Requirement: Recap identifies the topics discussed in the script
When generating an episode recap, the system SHALL also identify which of the candidate topic labels are actually discussed in the episode script, using the same LLM call that produces the recap (no additional request). The result SHALL be the subset of the provided candidate labels, matched by their exact label text.

After the recap is stored, the system SHALL set `topic_order` to `NULL` on every linked article whose topic is not in the discussed subset, while retaining the article link and its topic label. If the discussed subset is empty (e.g. the model returned nothing or the response could not be parsed), the system SHALL make no changes to `topic_order`.

When regenerating the recap for an existing episode, the system SHALL derive the candidate topic labels from the episode's existing linked articles (distinct topics with a non-null `topic_order`, in topic order) and pass them to recap generation, so that re-running recap regeneration corrects the discussed set for already-generated episodes.

#### Scenario: Recap prunes non-discussed topics
- **WHEN** recap generation runs for an episode whose links span 42 candidate topics and the model reports 15 discussed topics
- **THEN** the 27 non-discussed topics' articles have `topic_order` set to `NULL`, and the 15 discussed topics retain their `topic_order`

#### Scenario: Empty discussed subset is a no-op
- **WHEN** recap generation cannot determine any discussed topics
- **THEN** no `topic_order` values are changed

#### Scenario: Regeneration corrects an existing episode
- **WHEN** `regenerate-recap` is invoked for an episode that currently lists every linked topic
- **THEN** candidate labels are taken from the existing links, the discussed subset is recomputed, and non-discussed topics are pruned to background
