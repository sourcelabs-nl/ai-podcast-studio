## ADDED Requirements

### Requirement: Focus episode candidates
The focus episode candidates SHALL be every article from the podcast's sources published inside the run's window (after the evergreen and window filters), regardless of its relevance to the podcast's topic and regardless of whether another episode already used it (`is_processed`). Articles whose body is a pure retweet (starts with `RT @`) SHALL be excluded, because they carry no content of their own and a focus episode has no dedup stage. Replies and quote posts with their own text SHALL be kept.

#### Scenario: Article used by a regular episode stays a focus candidate
- **WHEN** a regular episode of the same window has selected the Anthropic announcement article and marked it processed, and a focus episode on that announcement is generated
- **THEN** the announcement article is a focus candidate and is scored against the focus text

#### Scenario: Pure retweet is excluded
- **WHEN** the window holds a post whose body is "RT @claudeai Introducing Claude Opus 5.5 ..."
- **THEN** that post is not a focus candidate

#### Scenario: Reply with its own content is kept
- **WHEN** the window holds a reply whose body is "@RLanceMartin Opus 5.5 is out! excellent at coding ..."
- **THEN** that reply is a focus candidate
