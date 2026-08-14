## ADDED Requirements

### Requirement: Script tab audio actions
Once a script preview exists, the Script tab SHALL offer two audio actions alongside the script statistics: "Play Sample" and "Generate Full Audio". Both SHALL use the `default` variant at `size="sm"` with a lucide icon, and SHALL be disabled while either action is running.

"Play Sample" SHALL POST the previewed script to `/api/users/{userId}/podcasts/{podcastId}/preview/audio/sample` and play the returned audio from a blob URL. It SHALL NOT ask for confirmation, because a sample costs about a cent. The blob URL SHALL be revoked when it is replaced and when the page unmounts.

"Generate Full Audio" SHALL first POST the script to `/api/users/{userId}/podcasts/{podcastId}/preview/audio/estimate` and present a confirmation dialog stating the character count and the estimated cost. Only the dialog's cancel button SHALL use the `outline` variant. On confirmation the client SHALL open the SSE stream at `/api/users/{userId}/podcasts/{podcastId}/preview/audio`, display per-chunk progress as "Synthesizing {chunk}/{total}", and on the `result` event play the audio from `/api/users/{userId}/podcasts/{podcastId}/preview/audio/{audioId}`.

The client SHALL read the audio stream with the same pattern as the script preview stream, carrying the current event name across reads so an event split across a network chunk boundary is still recognised. The proxy route serving the stream SHALL be dynamic and SHALL instruct intermediaries not to buffer.

#### Scenario: Audio actions appear only with a preview
- **WHEN** the Script tab is selected and no preview has been generated
- **THEN** neither audio action is displayed

#### Scenario: Play Sample clicked
- **WHEN** the user clicks "Play Sample"
- **THEN** the previewed script is posted to the sample endpoint and the returned audio plays inline, with no confirmation step

#### Scenario: Generate Full Audio asks for confirmation
- **WHEN** the user clicks "Generate Full Audio"
- **THEN** the estimate endpoint is called and a dialog states the character count and estimated cost before anything is synthesised

#### Scenario: Full audio generation cancelled
- **WHEN** the user cancels the confirmation dialog
- **THEN** no stream is opened and nothing is synthesised

#### Scenario: Full audio progress displayed
- **WHEN** the stream emits `{stage: "synthesizing", chunk: 12, total: 56}`
- **THEN** the tab displays "Synthesizing 12/56"

#### Scenario: Full audio completion plays the result
- **WHEN** the stream emits a `result` event carrying an `audioId`
- **THEN** the audio is played from the preview audio retrieval endpoint for that id

#### Scenario: Audio generation error
- **WHEN** the stream emits an `error` event or the request fails
- **THEN** the loading state is cleared and the message is displayed as an error banner
