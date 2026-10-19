## ADDED Requirements

### Requirement: A slot is served by an episode whose window ends at it

A slot SHALL count as served when an episode of the podcast has `window_end` equal to the slot,
is neither a focus nor an experiment episode, and has status GENERATING, GENERATING_AUDIO,
GENERATED, PENDING_REVIEW or APPROVED. A FAILED or DISCARDED episode SHALL NOT serve its slot.
The scheduler SHALL take the next due slot to be the first slot of the podcast's cron after the
latest served `window_end`, skipping slots of earlier days as before. Only when the podcast has no
served episode with a window SHALL it fall back to `last_generated_at`, and then to the start of
the current day. `last_generated_at` SHALL NOT otherwise decide whether a slot is due, and discard
SHALL NOT change it. The upcoming content SHALL be the articles published after the latest served
`window_end`, with the same fallbacks.

#### Scenario: A re-run after a discard serves the slot
- **WHEN** today's episode was discarded and its window was re-run as a new episode
- **THEN** the scheduler does not generate the slot again

#### Scenario: Discarding today's episode reopens its slot
- **WHEN** today's only episode for the 16:00 slot is discarded after 16:00
- **THEN** the slot is due again and the scheduler generates it

#### Scenario: An episode still generating serves the slot
- **WHEN** an episode for the slot is GENERATING
- **THEN** the scheduler does not start another one

#### Scenario: A missed day is absorbed, not generated separately
- **WHEN** the last served window ended on Friday 16:00 and the machine was off until Tuesday 17:00
- **THEN** only Tuesday's slot is generated, and its window starts at Friday 16:00

#### Scenario: The upcoming page starts where the last served window ended
- **WHEN** the latest served episode's window ends at 2026-10-19T14:00Z
- **THEN** upcoming lists unprocessed articles published from that instant on

### Requirement: A failed slot is retried once

When the only episodes whose window ends at a due slot are FAILED, the scheduler SHALL generate
the slot again as a new episode, once, and no sooner than 5 minutes after the failure. When two
episodes for the slot have failed, the scheduler SHALL NOT generate it again; a manual retry or
re-run still can. A FAILED episode SHALL record when it failed.

#### Scenario: One failure is retried after 5 minutes
- **WHEN** the slot's episode failed 3 minutes ago
- **THEN** nothing is generated yet, and once 5 minutes have passed a new episode is generated

#### Scenario: Two failures stop the scheduler
- **WHEN** two episodes for the slot have failed
- **THEN** the scheduler generates nothing for that slot

#### Scenario: A manual retry serves the slot again
- **WHEN** a failed episode is retried by hand and is GENERATING
- **THEN** it serves the slot and the scheduler starts nothing
