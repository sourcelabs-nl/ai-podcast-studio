## MODIFIED Requirements

### Requirement: Episode status lifecycle
Each episode SHALL have a `status` field with one of the following values: `PENDING_REVIEW`, `APPROVED`, `GENERATING_AUDIO`, `GENERATED`, `FAILED`, `DISCARDED`. The status determines where the episode is in the review-to-audio pipeline. `GENERATING_AUDIO` indicates that TTS audio generation is actively in progress. An episode can enter `FAILED` status either from a TTS generation failure (after approval) or from a pipeline generation error (e.g., invalid model configuration). In the pipeline error case, a FAILED episode is created with an empty `scriptText`, the error stored in `errorMessage`, and `lastGeneratedAt` updated to prevent scheduler retries. After each status transition, the service SHALL publish a `PodcastEvent` via `ApplicationEventPublisher` to notify connected clients.

A focus episode (one with a non-null `focus`) SHALL always be created with status `PENDING_REVIEW`, regardless of the podcast's `requireReview` setting.

#### Scenario: New episode created with review enabled
- **WHEN** the pipeline generates a script for a podcast with `requireReview = true`
- **THEN** an episode is created with status `PENDING_REVIEW`, the `scriptText` populated, `audioFilePath` and `durationSeconds` set to null, and an `episode.created` event is published

#### Scenario: New episode created without review
- **WHEN** the pipeline generates a script for a regular (non-focus) episode of a podcast with `requireReview = false`
- **THEN** the episode is created with status `GENERATED` after TTS completes, with all fields populated, and an `episode.generated` event is published

#### Scenario: Focus episode always requires review
- **WHEN** the pipeline generates a focus episode for a podcast with `requireReview = false`
- **THEN** the episode is created with status `PENDING_REVIEW`, exactly as it would be if `requireReview` were `true`

## ADDED Requirements

### Requirement: Focus episode review shows selection and research context
The review screen for a focus episode SHALL show: the articles selected for it, the research sources (query and results) recorded for it, and an estimated spoken length. The estimated length SHALL be computed from the script's word count using the same words-per-minute figure already used elsewhere in the product for this estimate.

#### Scenario: Review screen shows selected articles and sources
- **WHEN** a focus episode in `PENDING_REVIEW` is opened for review
- **THEN** the screen lists the articles linked to the episode and the research sources recorded for it

#### Scenario: Review screen shows an estimated length
- **WHEN** a focus episode's script contains 1500 words
- **THEN** the review screen shows an estimated spoken length derived from that word count using the product's existing words-per-minute figure

### Requirement: Feedback-driven recompose of a focus episode
A focus episode in `PENDING_REVIEW` SHALL support a feedback-driven recompose action: given a feedback text, the system recomposes the script (rerunning research) using the episode's already-selected articles, without re-running article selection. The action SHALL be repeatable. The feedback text SHALL be stored on the episode as `review_feedback`, holding the most recently submitted feedback. The episode SHALL remain in `PENDING_REVIEW` after a feedback-driven recompose.

#### Scenario: Recompose with feedback keeps the same articles
- **WHEN** a focus episode in `PENDING_REVIEW` receives a "make it more technical" feedback recompose request
- **THEN** the script is regenerated using the same set of linked articles, `review_feedback` is updated to "make it more technical", and the episode stays in `PENDING_REVIEW`

#### Scenario: Feedback recompose is repeatable
- **WHEN** a focus episode has already been recomposed once with feedback and receives a second, different feedback recompose request
- **THEN** the script is regenerated again against the same articles, and `review_feedback` is updated to the latest feedback text

#### Scenario: Approving after feedback recompose starts TTS as usual
- **WHEN** a focus episode that has gone through one or more feedback recomposes is approved
- **THEN** TTS generation starts through the existing approval flow, exactly as for any other `PENDING_REVIEW` episode
