# Spec Delta

## MODIFIED Requirements

### Requirement: X OAuth 2.0 PKCE authorization flow
The system SHALL provide an OAuth 2.0 PKCE authorization flow for X, following the same pattern as SoundCloud. An `XOAuthController` SHALL expose an authorization endpoint at `GET /users/{userId}/oauth/x/authorize` that generates a PKCE code verifier and challenge, creates an HMAC-signed state parameter, stores the code verifier in memory, and returns the X authorization URL. The authorization URL SHALL point to `https://twitter.com/i/oauth2/authorize` with `response_type=code`, `code_challenge_method=S256`, and scopes `tweet.read users.read offline.access`. The callback endpoint at `GET /oauth/x/callback` SHALL verify the state signature, exchange the authorization code for tokens via `POST https://api.x.com/2/oauth2/token`, encrypt the tokens, and store them in the `oauth_connections` table with provider `"x"`. When X redirects back with an `error` parameter, the callback SHALL return an HTML page stating that authorization failed, even when no `code` is present.

#### Scenario: Initiate X authorization
- **WHEN** a `GET /users/{userId}/oauth/x/authorize` request is received for an existing user
- **THEN** the system returns the X authorization URL with PKCE challenge and signed state parameter

#### Scenario: Initiate authorization when X is not configured
- **WHEN** a `GET /users/{userId}/oauth/x/authorize` request is received but `APP_X_CLIENT_ID` or `APP_X_CLIENT_SECRET` is not set
- **THEN** the system returns HTTP 503 indicating X integration is not configured

#### Scenario: OAuth callback with valid code
- **WHEN** the X callback is received with a valid authorization code and state
- **THEN** the system exchanges the code for tokens, encrypts and stores them in `oauth_connections` with provider `"x"`, and returns an HTML success page

#### Scenario: OAuth callback with invalid state
- **WHEN** the X callback is received with an invalid or tampered state parameter
- **THEN** the system returns HTTP 400

#### Scenario: OAuth callback with expired code verifier
- **WHEN** the X callback is received but the code verifier has expired (older than 10 minutes)
- **THEN** the system returns HTTP 400 with an error page indicating the authorization request has expired

#### Scenario: OAuth callback with a provider error and no code
- **WHEN** X redirects to the callback with an `error` parameter and without a `code`
- **THEN** the system returns an HTML page stating that X authorization failed, with the error text escaped
