# Tasks

## 1. Classify timeouts by a stable value

- [x] 1.1 Normalise a recognised timeout exception to the fixed `errorType`
  value `timeout` where a failed call is recorded, leaving every other failure
  with its class name; verify with a unit test that a read timeout records
  `timeout` and a provider rejection records its class name
- [x] 1.2 Count a timed-out request as latency: change the percentile query to
  include rows whose outcome is a failure identified as a timeout, keeping cache
  hits and every other failure excluded; verify with a repository test that a
  timeout row lands in the percentiles and sample count and a 529-style fast
  failure does not

## 2. Report stages that are not pipeline stages

- [x] 2.1 Introduce a reported-stage type naming the stage and the timeout its
  requests are issued with, and build the reported list from the pipeline stages
  plus the dedup gate with its own configured timeout; verify with a service
  test that the gate appears with a zero sample count when it issued nothing
- [x] 2.2 Verify the existing latency response shape is unchanged for the
  pipeline stages, so no frontend change is required: assert the API response
  for a pipeline stage carries the same fields and timeout as before

## 3. Record the Jev call

- [x] 3.1 Give `JevClient` the call log and a telemetry attribution parameter
  object (stage and episode id), recording one row per HTTP attempt timed around
  the exchange alone; verify with a test that a single successful call writes one
  row carrying the reported input tokens, the reported cost, zero output tokens
  and the `openrouter` provider
- [x] 3.2 Record a failed attempt as a failure carrying its kind, and a retried
  transient failure as one row per attempt; verify with a test that a 529
  followed by a success writes two rows, one failure and one success, and that
  neither duration includes the retry wait
- [x] 3.3 Write no row when no credential is configured and no request is
  issued; verify with a test that the existing no-key path records nothing
- [x] 3.4 Verify a failure to record does not change what the call returns:
  assert the gate's result is unaffected when the log write throws

## 4. Attribute the gate's call to its episode

- [x] 4.1 Add the episode id to `CoveredTopicGate.evaluate` and pass it from
  `TopicDedupFilter`, through to the recorded row; verify with a test that a gate
  evaluation during generation records a row naming that episode
- [x] 4.2 Update every existing test that calls `evaluate` or `ask` for the new
  signatures; verify `mvn test` passes

## 5. Verify end to end

- [x] 5.1 Run the full suite and confirm it is green
- [x] 5.2 Restart the application, generate or retry an episode, and confirm the
  gate's call appears in that episode's request list and in the latency response
  under its own stage with a non-zero sample count
- [x] 5.3 Record what the live run measured (gate latency against the clustering
  call, and whether any attempt failed) in `knowledge/`, per the bundle's record
  operation
