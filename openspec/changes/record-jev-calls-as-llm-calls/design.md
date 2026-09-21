# Design

## Where the record is written

`JevClient.ask` wraps the HTTP call rather than the whole method. The retry sits
inside the client, so timing the method would fold the wait between attempts
into the measurement and yield one record where there were two requests. Each
attempt is timed around the `RestClient` exchange alone and recorded before the
retry decides whether to try again.

`JevClient` takes `LlmCallLogService` as a constructor dependency, the same one
`CachingChatModel` uses. That service already runs its write in
`REQUIRES_NEW` and swallows every failure, which is exactly the contract this
call needs: the gate must return what it decided whether or not the row was
written.

The provider recorded is `openrouter`, matching the credential the call is
authorised with and the provider name used elsewhere for the same key. The
response's own `provider` field says `TypeSafe`, which is who served it, not who
the system has a relationship with, and using it would split the same account's
spend across two names.

Output tokens are recorded as zero. The endpoint bills them at zero and
frequently omits them, and inventing a count would put a number into the
telemetry that the provider never stated.

## Naming the stage

The gate records under its own stage name, `dedup-gate`, not under `dedup`. The
clustering call runs about 23s and the gate about 0.6s, and averaging the two
into one set of percentiles produces a p50 that describes neither. Keeping them
apart is the entire reason the gate is being made visible.

## Decoupling the reported stages from `PipelineStage`

`LlmCallLatencyService.stagesFor` currently iterates `PipelineStage.entries`, so
a row whose stage is outside the enum is written and never read back.

`PipelineStage` cannot simply gain a constant. It also drives model resolution
and timeouts through `default(StageDefaults)` and `timeout(StageTimeouts)`, and
the gate has neither: its model and timeout come from `app.llm.dedup.gate`. A
`DEDUP_GATE` constant would have to answer both methods with something invented.

Instead a reported stage becomes its own small type, naming the stage and the
timeout its requests are issued with. The service builds the list from the
pipeline stages plus the gate, and the response shape does not change: it
already carries `stage` and `timeoutMs` per entry, so the API gains a row rather
than a field and the frontend needs no change.

This is what makes the next Jev caller cheap. Adding a Jev check under the
scoring service is then one more entry in that list, not another enum constant
that has to pretend to be a pipeline stage.

## Which rows count as latency

The percentile query changes from `outcome = 'ok'` to `outcome = 'ok' OR
error_type = 'timeout'`, and keeps excluding cache hits.

The reason the previous rule excluded timeouts is sound as far as it goes: a
timeout's duration is the ceiling, not the provider's latency. But excluding
them biases the percentiles downward exactly where they are read, because the
requests that are dropped are the slowest ones. A p99 that rises to meet the
configured timeout is the honest reading of a saturated stage; a p99 computed
only over the requests that came back in time says the stage is healthy while it
is timing out.

Fast failures stay excluded. A 529 comes back in milliseconds, and counting it
would make an outage look like a latency improvement, which is precisely what
the gate's own incident would have produced.

This applies to every stage, not only the gate. Two rules in one table would
make the columns silently incomparable.

### Identifying a timeout

`errorType` currently holds the exception's simple class name, so the SQL would
otherwise have to match on `ResourceAccessException` or `SocketTimeoutException`
and would silently stop matching if a client changed. Timeout exceptions are
instead normalised at the point of writing to the fixed value `timeout`, and
every other failure keeps its class name.

Rows written before this change keep their class names and so are not counted.
The log is retained for a bounded period and the gap ages out; backfilling would
mean guessing which historical class names were timeouts.

## Threading the episode id

`CoveredTopicGate.evaluate` gains the episode id as a fourth parameter, passed
by `TopicDedupFilter`, which already has it. Four parameters stays under the
threshold at which the project's rules call for a parameter object, and the
existing three are already the question the gate is being asked.

`JevClient.ask` likewise gains the stage and episode id. `ask` already carries
four parameters, so these arrive in a small parameter object holding the
telemetry attribution (stage and episode), which keeps the call site readable
and leaves room for the next caller to attribute itself differently.

## What this change does not do

The gate's behaviour is untouched: what it excludes, what it costs, and its
refusal to empty an episode all stay as they are. No publish gate is introduced.
No migration runs, because `llm_calls` already has every column used here.
