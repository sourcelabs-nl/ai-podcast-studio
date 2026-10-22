# Proposal

## Why

On 2026-10-21 one Narro RSS poll, started at 10:21, hung for 5.5 hours until the OS reset the connection at 16:00. `RssFeedFetcher` installs its own `JdkClientHttpRequestFactory` to follow redirects, which drops the `spring.http.client` connect and read timeouts, and the JDK HTTP client has no read timeout by default. `SourcePollingScheduler.pollSources` waits for all host groups before the next round, so that one hung poll stopped all polling for about five hours and the day's episode generation was deferred.

## What Changes

- `RssFeedFetcher` sets a connect timeout and a read timeout on its HTTP client, from the new `app.source.feed-connect-timeout` (10s) and `app.source.feed-read-timeout` (30s).
- `SourcePollingScheduler` bounds each source's whole poll with `app.source.poll-timeout` (10m). A poll still running after it is logged and interrupted, and the round continues with the next source.

## Capabilities

### New Capabilities

### Modified Capabilities
- `source-polling`: adds "Feed fetches have connect and read timeouts" and "One source's poll is bounded in time".

## Impact

- `AppProperties.SourceProperties` (three durations), `application.yaml`, `RssFeedFetcher`, `SourcePollingScheduler`.
- Tests: `RssFeedFetcherTest` and `SourcePollingSchedulerTest`.
