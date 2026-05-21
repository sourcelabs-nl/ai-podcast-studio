## MODIFIED Requirements

### Requirement: Toast notifications for background events
The frontend SHALL display toast notifications for events that occur outside of the user's direct action. Toast-worthy events: `episode.created`, `episode.generated`, `episode.failed`, `episode.published`, `episode.publish.failed`, `episode.retrying`. The `pipeline.progress` and `episode.stage` events SHALL also display toasts with stage-specific messages.

#### Scenario: Audio generation complete toast
- **WHEN** an `episode.generated` event arrives
- **THEN** a success toast is shown with message "Episode #{number} audio ready"

#### Scenario: Audio generation failed toast
- **WHEN** an `episode.failed` event arrives
- **THEN** an error toast is shown with message "Episode #{number} audio generation failed"

#### Scenario: New episode from scheduler toast
- **WHEN** an `episode.created` event arrives (from scheduled briefing generation)
- **THEN** a toast is shown with message "New episode pending review"

#### Scenario: Publish success toast
- **WHEN** an `episode.published` event arrives
- **THEN** a success toast is shown with message "Episode #{number} published to {target}"

#### Scenario: Publish failed toast
- **WHEN** an `episode.publish.failed` event arrives
- **THEN** an error toast is shown with message "Episode #{number} publish failed"

#### Scenario: Retry initiated toast
- **WHEN** an `episode.retrying` event arrives with `resumePoint: "COMPOSE"` and `episodeNumber: 82`
- **THEN** an info toast is shown with message "Retrying episode #82 from COMPOSE..."

#### Scenario: User-initiated actions do not toast
- **WHEN** an `episode.approved` or `episode.discarded` event arrives (user just clicked the button)
- **THEN** no toast is shown
