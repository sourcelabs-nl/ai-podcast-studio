# Design

## Two layers

The read timeout fixes the cause: a feed request that never answers fails after 30 seconds as a transient error and goes through the existing backoff. The poll timeout is a backstop for any other place a poll can block (deep-fetches, storing), so one source can never hold up a round regardless of where it hangs.

## Interruption

The poll runs inside `withTimeoutOrNull` around `runInterruptible`, so the blocked thread is interrupted when the timeout fires rather than left running. A timed-out poll returns "not skipped" and is retried when it is next due.

## Values

10 seconds to connect and 30 seconds to read are generous for a feed. 10 minutes for a whole poll is far above a normal poll, so it only fires on a real hang.
