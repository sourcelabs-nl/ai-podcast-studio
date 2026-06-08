## ADDED Requirements

### Requirement: Deep-fetch full article body from RSS links
When deep-fetch is enabled, the RSS fetcher SHALL retrieve the page behind each entry's link, extract its main text via the shared content extractor, and use that text as the post body in preference to the feed-provided body whenever the extracted text is richer (longer) than the feed body. When the extracted text is not richer, missing, or the fetch fails, the fetcher SHALL fall back to the feed body without failing the poll. Deep-fetch SHALL be controlled by `app.source.deep-fetch.enabled` (default true) with a configurable timeout (`timeoutMs`, default 15000).

#### Scenario: Richer article text replaces a summary-only feed body
- **WHEN** deep-fetch is enabled and the linked page yields text longer than the feed entry's body
- **THEN** the saved post body is the extracted article text

#### Scenario: Feed body retained when extraction is not richer
- **WHEN** the extracted text is shorter than the feed body, blank, or the fetch throws an error
- **THEN** the saved post body is the original feed body and the poll continues

#### Scenario: Deep-fetch disabled
- **WHEN** `app.source.deep-fetch.enabled` is false (or deep-fetch is not requested for the source type)
- **THEN** the fetcher uses the feed body and never fetches the linked page

### Requirement: Skip non-article hosts and source types
The RSS fetcher SHALL NOT deep-fetch links whose host matches any configured skip-host substring (default: `x.com`, `twitter.com`, `nitter`, `youtube.com`, `youtu.be`), nor when the link is unparseable. YouTube sources, which are polled through the RSS fetcher, SHALL be polled with deep-fetch disabled.

#### Scenario: Configured host is skipped
- **WHEN** an RSS entry links to a host containing a configured skip term (e.g. `x.com`)
- **THEN** the fetcher keeps the feed body and does not attempt a deep-fetch

#### Scenario: YouTube source polled without deep-fetch
- **WHEN** a source of type YOUTUBE is polled
- **THEN** the RSS fetcher is invoked with deep-fetch disabled
