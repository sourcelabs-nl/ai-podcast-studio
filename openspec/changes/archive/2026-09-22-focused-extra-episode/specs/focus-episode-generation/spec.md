## Purpose

Defines how a one-off, focus-scoped episode is generated on demand: it is scored against a free-text focus instead of the podcast's topic, keeps only focus-relevant articles, and leaves the podcast's regular schedule and article pool untouched.

## ADDED Requirements

### Requirement: Focus text starts a focus episode
A manual generation request MAY include a non-blank `focus` string. When present, the run SHALL be a focus episode: an episode created with its `focus` column set to that text. When `focus` is absent or blank, generation SHALL proceed exactly as before (a regular episode, `focus` left null).

#### Scenario: Generate with focus text
- **WHEN** a manual generation request includes `focus = "Claude Opus 5.5 release"`
- **THEN** the created episode has `focus = "Claude Opus 5.5 release"` and is generated as a focus episode

#### Scenario: Generate without focus text
- **WHEN** a manual generation request omits `focus` or sends it blank
- **THEN** the created episode has `focus = null` and generation behaves exactly as it did before focus episodes existed

### Requirement: Focus episode uses the current article window
A focus episode SHALL select from the same article window a regular manual generation would use at that moment (the window `EpisodeWindowResolver` resolves for now), not a window scoped to the focus topic's own history.

#### Scenario: Focus episode shares the current window
- **WHEN** a focus episode and a regular manual generation are both triggered at the same moment for the same podcast
- **THEN** both resolve to the same article window

### Requirement: Focus episode scores articles against the focus text
A focus episode's scoring stage SHALL judge each candidate article's relevance against the supplied focus text instead of the podcast's `topic`. The podcast's own `relevanceThreshold` and other scoring behavior (summary, news-type classification) SHALL otherwise be unchanged.

#### Scenario: Focus-relevant article scores high
- **WHEN** an article about "Claude Opus 5.5 release" is scored for a focus episode with `focus = "Claude Opus 5.5 release"`
- **THEN** the relevance score reflects how well the article matches the focus text, not the podcast's general topic

#### Scenario: Podcast-topic-relevant but focus-irrelevant article scores low
- **WHEN** an article is highly relevant to the podcast's general topic but unrelated to the focus text
- **THEN** the article scores low relevance for this focus episode and is not selected

### Requirement: Only focus-relevant articles are kept
A focus episode SHALL keep only articles that clear the podcast's `relevanceThreshold` against the focus text. Articles that pass the podcast's general topic but not the focus are excluded from the focus episode, and are not marked processed by it (see the article-tracking requirement below), so a regular episode can still consider them on their own merits.

#### Scenario: Mixed candidate pool
- **WHEN** a focus episode's candidate pool contains 10 articles, 3 of which clear the relevance threshold against the focus text
- **THEN** the focus episode composes from exactly those 3 articles

### Requirement: No relevant articles fails the run with a clear message
When no candidate article clears the relevance threshold against the focus text, the focus episode generation SHALL fail rather than compose an empty or off-topic script. The failure message SHALL name the focus text and state that no relevant articles were found.

#### Scenario: Focus with zero matches
- **WHEN** a focus episode is generated for `focus = "Claude Opus 5.5 release"` and no candidate article scores above the relevance threshold
- **THEN** the episode ends in `FAILED` status with an error message that names the focus text and explains that no relevant articles were found

### Requirement: Focus episode does not advance the regular schedule
Creating a focus episode SHALL NOT update the podcast's `lastGeneratedAt`. The window a subsequent regular episode resolves SHALL be unaffected by a focus episode having run in between.

#### Scenario: Regular schedule unaffected by a focus episode
- **WHEN** a focus episode is generated between two regular episodes
- **THEN** the podcast's `lastGeneratedAt` is unchanged by the focus episode, and the next regular episode's window starts where it would have if the focus episode had never run
