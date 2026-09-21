# Capability: Article Eligibility

## Purpose

Centralized article eligibility decisions for pipeline article selection and episode discard reset logic.

## Requirements

### Requirement: Centralized article eligibility service
The system SHALL provide an `ArticleEligibilityService` that is the single source of truth for article selection and lifecycle management. All pipeline code paths (`run()`, `recompose()`, `preview()`) and episode management operations (`discardAndResetArticles()`) SHALL delegate article eligibility decisions to this service.

#### Scenario: Pipeline run delegates article selection
- **WHEN** `LlmPipeline.run()` needs to find candidate articles for composition
- **THEN** it calls `ArticleEligibilityService` to get eligible articles rather than querying the repository directly

#### Scenario: Pipeline recompose delegates article selection
- **WHEN** `LlmPipeline.recompose()` needs to validate and prepare articles for composition
- **THEN** it delegates to `ArticleEligibilityService` for any eligibility checks

#### Scenario: Discard delegates reset guard
- **WHEN** `EpisodeService.discardAndResetArticles()` needs to determine which articles can be reset
- **THEN** it calls `ArticleEligibilityService.canResetArticle()` for each article

### Requirement: Discard reset guard
When an episode is discarded, the system SHALL only reset `isProcessed = false` for articles that are NOT linked to any GENERATED episode that has at least one publication with status `PUBLISHED`. Articles linked to published episodes SHALL retain their `isProcessed = true` flag.

#### Scenario: Article only linked to discarded episode -- reset allowed
- **WHEN** an episode is discarded and one of its articles is only linked to this episode (no other episodes)
- **THEN** the article's `isProcessed` is reset to `false`

#### Scenario: Article linked to both discarded and published episode -- reset blocked
- **WHEN** an episode is discarded and one of its articles is also linked to a GENERATED episode that has a PUBLISHED publication
- **THEN** the article's `isProcessed` remains `true`

#### Scenario: Article linked to GENERATED episode without publications -- reset allowed
- **WHEN** an episode is discarded and one of its articles is also linked to a GENERATED episode that has no publications
- **THEN** the article's `isProcessed` is reset to `false` (unpublished GENERATED episodes are not "final")

#### Scenario: Aggregated article linked to published episode -- deletion blocked
- **WHEN** an episode is discarded and one of its aggregated articles (2+ linked posts) is also linked to a GENERATED episode with publications
- **THEN** the aggregated article and its `post_articles` entries are NOT deleted

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

### Requirement: Article window filter
The system SHALL exclude candidate articles whose publication instant falls outside the run's
article window, which the caller supplies rather than the eligibility service deriving it. The
window is half-open: an article is kept when its instant is at or after the window start and
strictly before the window end. Publication instants SHALL be compared as instants, not as strings,
because `published_at` is written by several fetchers and an offset form or a differing precision
would order wrongly under a lexicographic comparison. An article whose `published_at` is absent or
unparseable SHALL be kept, so content from sources that publish without a usable date is not
silently lost.

#### Scenario: New source with old content excluded
- **WHEN** a new RSS source is added and its articles have `published_at` dates from last week, and the run's window covers the last 24 hours
- **THEN** none of the old articles are included as candidates

#### Scenario: New source with fresh content included
- **WHEN** a new RSS source is added and it has articles published inside the run's window
- **THEN** those articles are included as candidates

#### Scenario: An article published after the window is excluded
- **WHEN** an article was published after the window end, for instance while a deferred run was waiting
- **THEN** it is not a candidate for this run and stays available to the next window

#### Scenario: The window boundaries are half-open
- **WHEN** one article is published exactly at the window start and another exactly at the window end
- **THEN** the first is a candidate and the second is not

#### Scenario: An offset timestamp is placed by instant
- **WHEN** an article's `published_at` is the same instant as the window start but written with a `+01:00` offset rather than `Z`
- **THEN** it is a candidate, because instants are compared rather than strings

#### Scenario: An article without a usable date is kept
- **WHEN** an article has no `published_at`, or one that cannot be parsed
- **THEN** it is kept as a candidate and the unparseable value is logged at WARN
