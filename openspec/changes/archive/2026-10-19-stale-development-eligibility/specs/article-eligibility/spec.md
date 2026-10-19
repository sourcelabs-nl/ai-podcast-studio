## ADDED Requirements

### Requirement: Developments that happened before the window are not eligible

An article classified `DEVELOPMENT` whose development date lies more than one day before the start
of the episode's window SHALL be excluded from the candidates. The one day of slack absorbs a
development date that carries no time of day and a window that starts in the afternoon.

The publication window cannot do this. A feed can publish, or republish, a write-up of an old
development with a new date, so its `published_at` falls inside the window while the development
it reports does not.

Every excluded article SHALL be logged with its title, URL and development date.

An article with no development date, an unparseable one, or a classification other than
`DEVELOPMENT` SHALL be kept: a retrospective is new analysis of something known, and a date the
pipeline failed to obtain must not silently delete content.

#### Scenario: A republished September development is excluded
- **WHEN** an article classified `DEVELOPMENT` with development date 2026-09-28 is published inside the window starting 2026-10-16T14:00Z
- **THEN** it is not eligible, and a log line names its title, URL and date

#### Scenario: A development from the day before the window is kept
- **WHEN** an article classified `DEVELOPMENT` has development date 2026-10-15 and the window starts 2026-10-16T14:00Z
- **THEN** it remains eligible

#### Scenario: A retrospective of an old development is kept
- **WHEN** an article classified `RETROSPECTIVE` has development date 2026-09-01
- **THEN** it remains eligible

#### Scenario: An article without a development date is kept
- **WHEN** an article classified `DEVELOPMENT` has no development date
- **THEN** it remains eligible
