## ADDED Requirements

### Requirement: Preview progress reaches the browser as it happens
Progress events for the script preview SHALL reach the browser while the pipeline is running, not in a single batch when it completes.

The server SHALL NOT compress proxied event streams. Compression buffers a stream of small events until its buffer fills, which withholds every progress event until the run ends. Browsers always request a compressed encoding, so a stream that relies on the client opting out of compression is a stream that never updates in practice.

#### Scenario: Progress arrives during the run
- **WHEN** a script preview is running and the pipeline emits a progress event
- **THEN** the browser receives that event while the pipeline is still running and the displayed stage label updates

#### Scenario: Stream is not compressed
- **WHEN** the browser requests an event stream through the dashboard with its usual `Accept-Encoding: gzip`
- **THEN** the response is not gzipped and the first event arrives without waiting for a compression buffer to fill
