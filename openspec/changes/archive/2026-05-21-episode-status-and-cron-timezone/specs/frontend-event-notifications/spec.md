## MODIFIED Requirements

### Requirement: Toast notifications for background events
The frontend SHALL display toast notifications for events that occur outside of the user's direct action. Toast-worthy events: `episode.created`, `episode.audio.started`, `episode.generated`, `episode.failed`, `episode.published`, `episode.publish.failed`.

#### Scenario: Audio generation started toast
- **WHEN** an `episode.audio.started` event arrives
- **THEN** an info toast is shown with message "Episode #{number} audio generating..."

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