## ADDED Requirements

### Requirement: Evergreen articles are not eligible

An article classified `EVERGREEN` carries no datable development and SHALL be excluded from the
candidates for an episode.

The publication window cannot do this. An evergreen page's `published_at` is the moment it was
linked or submitted rather than the moment anything happened, so it falls inside whatever window it
is offered to and is indistinguishable from a genuine announcement by date alone.

Every excluded article SHALL be logged with its title and URL, so a misclassification is visible
rather than inferred from a thin episode.

An article whose classification is absent or unrecognised SHALL be kept, on the same grounds as an
article with no `published_at`: a classification the pipeline failed to obtain must not silently
delete content, and every article scored before the classification existed carries none.

#### Scenario: A landing page inside the window is still excluded

- **WHEN** an article classified `EVERGREEN` carries a `published_at` inside the episode's window
- **THEN** it is not eligible, and a log line names its title and URL

#### Scenario: An unclassified article is kept

- **WHEN** an article carries no classification, or one the pipeline does not recognise
- **THEN** it is treated exactly as before and remains eligible

### Requirement: Episode history exposes covered topics

The history supplied for deduplication SHALL carry both the articles recent episodes drew on and the
dedup cluster topic labels those episodes stored, gathered from the same episode links.

Topic labels SHALL NOT be capped by the historical-article limit: one label per cluster is far
smaller than the articles behind it, and a dropped label is a topic the filter forgets.

#### Scenario: Topics come back with the articles

- **WHEN** history is read for a podcast whose recent episodes carry cluster topic labels
- **THEN** the result carries those labels, without repeats, alongside the historical articles

#### Scenario: No generated episodes

- **WHEN** the podcast has no generated episodes
- **THEN** the result carries neither articles nor topics
