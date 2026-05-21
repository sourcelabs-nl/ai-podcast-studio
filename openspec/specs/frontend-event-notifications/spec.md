# Capability: Frontend Event Notifications

## Purpose

Real-time event handling in the frontend via SSE, enabling automatic data refetching and toast notifications for background events.
## Requirements
### Requirement: EventProvider React context
The frontend SHALL provide an `EventProvider` component that wraps the application in `layout.tsx` (alongside the existing `UserProvider`). It SHALL open an `EventSource` connection to `/api/users/{userId}/events` when a user is selected, and close it when the user changes or the component unmounts.

#### Scenario: Connection established on user selection
- **WHEN** a user is selected in the `UserProvider` context
- **THEN** the `EventProvider` opens an `EventSource` connection to `/api/users/{userId}/events`

#### Scenario: Connection closed on user change
- **WHEN** the selected user changes from "u1" to "u2"
- **THEN** the `EventSource` for "u1" is closed and a new one is opened for "u2"

#### Scenario: Auto-reconnect on connection drop
- **WHEN** the SSE connection drops (network error, server restart)
- **THEN** `EventSource` automatically reconnects (built-in browser behavior)

### Requirement: useEventStream hook
The frontend SHALL provide a `useEventStream` hook that allows components to subscribe to SSE events. The hook SHALL accept an optional `podcastId` filter and a callback function. When an event arrives matching the filter (or all events if no filter), the callback is invoked with the parsed event data.

#### Scenario: Component subscribes to podcast events
- **WHEN** the episode list page calls `useEventStream("podcast-abc", callback)`
- **THEN** the callback is invoked only for events where `podcastId === "podcast-abc"`

#### Scenario: Component subscribes to all events
- **WHEN** a component calls `useEventStream(null, callback)`
- **THEN** the callback is invoked for all events regardless of podcastId

#### Scenario: Subscription cleanup on unmount
- **WHEN** a component using `useEventStream` unmounts
- **THEN** the subscription is removed and the callback is no longer invoked

### Requirement: Event-driven data refetch
Frontend pages SHALL use `useEventStream` to trigger data refetches when relevant events arrive, replacing the current pattern of only refetching after user actions.

#### Scenario: Episode list refetches on episode event
- **WHEN** the episode list page is open for podcast "abc" and an `episode.generated` event arrives for podcast "abc"
- **THEN** the page refetches the episodes list from the REST API

#### Scenario: Episode detail refetches on episode event
- **WHEN** the episode detail page is open for episode 42 and an `episode.generated` event arrives for entity ID 42
- **THEN** the page refetches the episode details from the REST API

#### Scenario: Unrelated event is ignored
- **WHEN** the episode list page is open for podcast "abc" and an event arrives for podcast "xyz"
- **THEN** no refetch is triggered

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

### Requirement: Sonner toast component
The frontend SHALL use the shadcn/ui Sonner toast component for displaying notifications. The `<Toaster />` component SHALL be mounted in `layout.tsx`.

#### Scenario: Toast renders correctly
- **WHEN** a toast notification is triggered
- **THEN** a Sonner toast appears in the bottom-right corner with the appropriate variant (success, error, or info)

