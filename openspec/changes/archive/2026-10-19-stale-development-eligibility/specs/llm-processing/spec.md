## ADDED Requirements

### Requirement: Scoring records the development date

The scoring call SHALL return `developmentDate`: the calendar date (`YYYY-MM-DD`) on which the
content states the development it reports happened. For a retrospective it is the date the examined
thing was released. It SHALL be null when the content states no such date; the model SHALL NOT
infer it from the publication date or from relative wording. The value SHALL be stored on the
article. A value that is not a valid date SHALL be stored as null.

#### Scenario: A stated release date is returned
- **WHEN** the content says a feature shipped in a release published September 28, 2026
- **THEN** the scoring result carries `developmentDate` 2026-09-28 and the article stores it

#### Scenario: No stated date
- **WHEN** the content names no date for the development
- **THEN** `developmentDate` is null

#### Scenario: An invalid date is dropped
- **WHEN** the model answers `developmentDate` with a value that is not a valid date
- **THEN** the article stores null
