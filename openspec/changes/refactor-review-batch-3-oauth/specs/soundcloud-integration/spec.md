# Spec Delta

## MODIFIED Requirements

### Requirement: SoundCloud OAuth callback
The system SHALL provide a `GET /oauth/soundcloud/callback` endpoint that handles the OAuth redirect from SoundCloud. The endpoint SHALL validate the `state` parameter's HMAC signature, extract the `userId`, retrieve the code verifier from the in-memory cache, exchange the authorization code for tokens via `POST https://api.soundcloud.com/oauth2/token`, encrypt the access and refresh tokens using `ApiKeyEncryptor`, and store them in the `oauth_connections` table. On success, the endpoint SHALL return an HTML page indicating the connection was successful. When SoundCloud redirects back with an `error` parameter, the endpoint SHALL return an HTML page stating that authorization failed, even when no `code` is present.

#### Scenario: Successful callback
- **WHEN** SoundCloud redirects to `/oauth/soundcloud/callback?code=abc&state=xyz` with a valid state and code
- **THEN** the system exchanges the code for tokens, stores encrypted tokens in `oauth_connections`, and returns an HTML page saying "SoundCloud connected successfully! You can close this tab."

#### Scenario: Invalid state parameter
- **WHEN** the callback is received with a state parameter that has an invalid HMAC signature
- **THEN** the system returns HTTP 400 with an error page indicating the authorization request is invalid

#### Scenario: Expired state (code verifier not found)
- **WHEN** the callback is received with a valid state but the code verifier has expired from the cache
- **THEN** the system returns HTTP 400 with an error page indicating the authorization request has expired

#### Scenario: Token exchange fails
- **WHEN** the callback is received with a valid state and code but SoundCloud rejects the token exchange
- **THEN** the system returns an HTML error page indicating the connection failed

#### Scenario: Re-authorization replaces existing connection
- **WHEN** the callback completes for a user who already has an `oauth_connections` record for `"soundcloud"`
- **THEN** the existing record is updated with the new encrypted tokens and expiry

#### Scenario: Provider error and no code
- **WHEN** SoundCloud redirects to the callback with an `error` parameter and without a `code`
- **THEN** the system returns an HTML page stating that SoundCloud authorization failed, with the error text escaped
