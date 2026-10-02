# Proposal

## Why

The code review of 2026-10-01 found the SoundCloud and X OAuth controllers to be near-identical copies that run the whole authorization flow themselves: PKCE verifier storage, state checks, token exchange and connection storage. They have drifted: an expired authorization returns 400 for SoundCloud and 404 for X, and both declare `code` and `state` as required, so a provider that redirects back with only an `error` (the user denied access) fails with a bare 400 instead of showing the failure page. Controllers must validate, delegate and map.

## What Changes

- A shared `OAuthPkceFlow` holds the pending verifiers and the state checks, one instance per provider so a state issued for one provider is never accepted by the other.
- A `SoundCloudOAuthService` and an `XOAuthService` own the authorize URL, the callback handling and the status lookup. The controllers validate the user, delegate and map the outcome.
- One callback outcome type and one page renderer replace the duplicated `htmlPage` and `htmlResponse` helpers.
- **BEHAVIOR**: an expired X authorization now returns HTTP 400, like SoundCloud.
- **BEHAVIOR**: both callbacks accept a redirect that carries only `error` and `state` and show the failure page.

## Capabilities

### New Capabilities

### Modified Capabilities
- `twitter-oauth`: an expired code verifier returns 400, and a provider error redirect without a code shows the failure page.
- `soundcloud-integration`: a provider error redirect without a code shows the failure page.

## Impact

- `publishing/SoundCloudOAuthController.kt`, `source/XOAuthController.kt`, `publishing/OAuthConnectionService.kt` (callers only), new files in `publishing/` for the flow, the two services, the outcome type, the page renderer and the outcome mapper.
- Controller tests are reduced to request mapping; flow and service behavior get their own unit tests.
