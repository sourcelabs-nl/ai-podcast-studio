# Design

## Retry only the fetch, only for dropped connections

A connection that was reset, refused or closed says nothing about the source itself and usually works seconds later, while an HTTP status or a timeout is a real answer from the host. `SourcePoller` wraps the fetch step in a helper that catches the exception, checks the cause chain for a `SocketException`, waits `app.source.connection-retry-delay` and calls the fetch once more. Storing, deep-fetching and failure tracking stay outside the wrapper, so a successful retry is an ordinary success and a persistent drop is one failure for backoff, not two.

## Log a deferred slot once at info

The scheduler keeps the set of slots (`podcastId@slot`) whose deferral it has logged. The first check of a slot logs at info, every later per-minute re-check of the same slot logs at debug. The set grows by one entry per deferred slot.
