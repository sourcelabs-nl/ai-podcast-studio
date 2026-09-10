## Context

`PublishingService.publish()` wrapped the upload and everything that follows it in one `try`, and its catch saved a `FAILED` copy of the record snapshot taken before the upload. That made every post-upload step able to erase a successful publication's external id, and made cancellation indistinguishable from a genuine upload failure.

## Decisions

### A failed side effect is logged, not thrown

Each side effect runs through `runSideEffect`, which catches and logs. The publish call then returns successfully with a `PUBLISHED` record even though, say, the static feed was not re-exported.

The alternative is to keep propagating the exception while leaving the record `PUBLISHED`. It was rejected because the caller has nothing useful to do with it: the auto-publish listener and the publish endpoint both read a thrown exception as "this episode is not published", which is exactly the wrong conclusion and the one that started this. The signal for a broken side effect is the WARN, which names the step and the target.

`CancellationException` is exempt and always rethrown, so cancelling a publish still tears down its coroutine promptly instead of running the remaining side effects.

### A cancelled publish leaves the record PENDING

The other options both assert something unknown. `FAILED` claims the upload did not land and that nothing remote needs cleaning up, which is how episode 202's track was orphaned. `PUBLISHED` cannot be written at all, since a cancelled call returns no external id.

`PENDING` is the honest state and is not a dead end: `publish()` treats any record that is not `PUBLISHED` as a claim to overwrite, so the next publish of that episode proceeds normally.

### The concurrent-claim race is left alone

Two simultaneous publishes of the same episode and target would both find no record, both insert, and one would hit the `UNIQUE (episode_id, target)` constraint. That is a real hole, but it is not what happened here (a single call corrupted its own row) and closing it needs an idempotent claim plus a decision about which caller wins. It stays out of this change.

## Risks

- A silent side-effect failure is now only visible in the log. The static feed export is the one that matters: if it fails, the RSS feed lags behind the publication until the next publish. Accepted, because reporting a completed publish as a failure is worse and was demonstrably damaging.
