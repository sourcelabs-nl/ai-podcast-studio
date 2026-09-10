## MODIFIED Requirements

### Requirement: Briefing generation resolves and passes the article window
The `BriefingGenerationScheduler` SHALL resolve the article window for the cron slot it is serving
and pass it into generation, so the window is decided by the caller that knows which slot is being
served and is recorded on the episode. The window SHALL end at the slot instant rather than at the
current time. Before generating, the scheduler SHALL check that the podcast's sources have polled
the window and defer the run when they have not, within the bound defined by the
`episode-article-window` capability. Deferring SHALL leave `last_generated_at` untouched so the
slot stays due for the next tick. A manual generation, which serves no slot, SHALL resolve a window
ending at the current instant.

#### Scenario: The scheduled run passes the slot's window
- **WHEN** the scheduler finds a podcast due for its 15:00 slot
- **THEN** it resolves the window for that slot and generation records it on the episode

#### Scenario: A deferred run leaves the slot due
- **WHEN** generation is deferred because a source is behind the window
- **THEN** no episode is created, `last_generated_at` is unchanged, and the next tick re-evaluates the same slot

#### Scenario: A manual generation resolves its own window
- **WHEN** an episode is generated from the dashboard outside any slot
- **THEN** the window ends at the current instant and is recorded on the episode
