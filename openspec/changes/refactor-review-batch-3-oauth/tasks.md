# Tasks

## 1. Shared flow

- [x] 1.1 Add `OAuthPkceFlow` (begin and complete, 10 minute TTL, entry removed on complete), and verify with unit tests for a valid state, a tampered state, an expired entry, a reused state and two flow instances not accepting each other's state
- [x] 1.2 Add `OAuthCallbackOutcome`, `OAuthCallbackPage` and the outcome mapper, and verify with tests that each outcome yields the expected status and an escaped page

## 2. Providers

- [x] 2.1 Add `SoundCloudOAuthService` (authorize URL, callback handling, status with quota, deleteTrack) and slim `SoundCloudOAuthController` to user checks, delegation and mapping, and verify with service tests for each outcome and the existing controller tests
- [x] 2.2 Add `XOAuthService` and slim `XOAuthController` the same way, with an expired authorization answering 400, and verify with service and controller tests
- [x] 2.3 Make `code` and `state` optional on both callbacks and verify with controller tests that an `error` redirect without a code shows the escaped failure page and that a missing code without an error answers 400

## 3. Completion

- [x] 3.1 Run `mvn test`, restart the app, and re-run `/code-review` over the OAuth files until this batch's findings are gone
