# Design

## Context

See proposal.md. Both controllers hold a `ConcurrentHashMap<String, PendingOAuth>` of PKCE verifiers with a 10 minute TTL, build the authorize URL, run the token exchange and store the connection. The only differences are the provider constants, the credential source (SoundCloud resolves per user, X reads `AppProperties`), the scopes and the expired-status code.

## Goals / Non-Goals

**Goals:** controllers contain no OAuth flow; the verifier map and page rendering exist once; both providers report the same outcomes with the same status codes.

**Non-Goals:** the inline `try/catch` of `deleteTrack` (a later batch), changing scopes, token storage or the PKCE algorithm, and merging the two providers into one generic service.

## Decisions

- **`OAuthPkceFlow` is a plain class, not a bean.** Each provider service creates its own instance over the shared `OAuthPkceHelper`. A singleton would share one verifier map, and the state is signed with one key, so a state issued by the SoundCloud authorize could then complete an X callback. Interface: `begin(userId)` returns the signed state and the code challenge and stores the verifier; `complete(state)` returns `Verified(userId, codeVerifier)`, `InvalidState` or `Expired` and removes the pending entry.
- **Outcome type.** `OAuthCallbackOutcome` is a sealed type with `Connected`, `ProviderRefused(error)`, `InvalidState`, `Expired` and `ExchangeFailed`. The services return it; `OAuthCallbackMappers.kt` turns it into the HTML response (200 for `Connected`, `ProviderRefused` and `ExchangeFailed` as today, 400 for `InvalidState` and `Expired`).
- **Page rendering** moves to one `OAuthCallbackPage.render(title, message)` that escapes both arguments.
- **Provider error callbacks.** `code` and `state` become optional request parameters. With `error` present the page shows the failure, whatever else is missing; without `error`, a missing `code` or `state` is an `InvalidState` outcome.
- **Services.** `SoundCloudOAuthService` takes the credential resolver, the SoundCloud client, the connection service and the helper; `XOAuthService` takes `AppProperties`, the X client, the connection service and the helper. Each offers `isConfigured`, the authorize URL, `handleCallback(code, state, error)` and, for SoundCloud, the status with quota and `deleteTrack`. Controllers keep the user-exists check and the 503 mapping.

## Risks / Trade-offs

- [A client relied on 404 for an expired X authorization] -> The only caller is a browser landing page; the spec and this change record the new code.
- [A shared verifier map would cross-accept providers] -> One flow instance per provider, covered by a test.
