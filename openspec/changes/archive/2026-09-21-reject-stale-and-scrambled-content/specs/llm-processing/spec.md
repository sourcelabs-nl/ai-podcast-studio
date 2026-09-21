## ADDED Requirements

### Requirement: Scoring places every article in time

Compose reads an article's body only when the run is smaller than
`app.briefing.full-body-threshold`, which a normal day's article count is not, so for most episodes
the summary is the only view of the article that anything downstream has. Whatever the summary drops
about when something happened cannot be recovered. The scoring stage SHALL therefore classify each
article while the body is still in hand.

The scoring call SHALL return a `newsType` of exactly one of `DEVELOPMENT` (something presented as
newly happened), `RETROSPECTIVE` (analysis, benchmark or commentary about something already released
or already known) or `EVERGREEN` (a landing page, README, documentation or marketing copy with no
datable event). The value SHALL be persisted on the article. A value the pipeline does not recognise
SHALL be stored as no classification rather than rejected, and SHALL be logged.

The summary SHALL be consistent with that classification, and SHALL NOT assert an event the content
does not report. A `RETROSPECTIVE` SHALL lead with what is newly revealed or measured and SHALL NOT
describe the thing it examines as newly released.

#### Scenario: A retrospective is not summarised as a release

- **WHEN** an article analysing an already-released model's technical report is scored, its own text
  saying the model "was released" some time earlier
- **THEN** it is classified `RETROSPECTIVE`, and the summary describes what the report reveals rather
  than opening "X released Y"

#### Scenario: A landing page is classified evergreen

- **WHEN** a project's homepage is scored, carrying a star count, an install command and a
  compatibility list and no event
- **THEN** it is classified `EVERGREEN`

#### Scenario: An unrecognised classification is treated as unknown

- **WHEN** the scoring model answers with a `newsType` outside the three defined values
- **THEN** the article is stored with no classification, a warning is logged, and scoring otherwise
  succeeds
