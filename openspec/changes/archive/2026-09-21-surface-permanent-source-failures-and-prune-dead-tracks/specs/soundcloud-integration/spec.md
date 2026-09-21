## ADDED Requirements

### Requirement: A rebuild records the tracks that no longer exist

Freeing upload quota deletes this podcast's oldest SoundCloud tracks without touching their
publication rows, which keep a `PUBLISHED` status and a track id that no longer resolves. Asking
SoundCloud about those ids on every rebuild is both wasted work and the dominant source of log
noise, and it leaves the dashboard claiming an episode is published where it is not.

When a playlist rebuild finds that a track returns HTTP 404, the system SHALL record that the
episode is no longer published on SoundCloud: the publication's status SHALL become `UNPUBLISHED`
and its `externalId` SHALL be cleared. A later rebuild SHALL therefore not ask about that track
again. Publications whose tracks still exist SHALL be left untouched. The episode SHALL remain
publishable, so the user can put it back on SoundCloud if they want it there.

#### Scenario: A dead track's publication is corrected

- **WHEN** a playlist rebuild finds that one of two published tracks returns HTTP 404
- **THEN** that publication becomes `UNPUBLISHED` with a cleared `externalId`, the other publication
  is unchanged, and the playlist is rebuilt from the surviving track

#### Scenario: A corrected publication is not asked about again

- **WHEN** a rebuild runs after a previous rebuild corrected a dead track's publication
- **THEN** the corrected publication is no longer among the published tracks, so no request is made
  for its former track id

#### Scenario: A rebuild with no dead tracks writes nothing

- **WHEN** a playlist rebuild finds that every published track still exists
- **THEN** no publication row is written
