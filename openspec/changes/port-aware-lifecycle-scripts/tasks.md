## 1. Shared helpers

- [x] 1.1 Add `lib-process.sh` with port lookup, live-PID-from-file, descendant walk, combined PID resolution, graceful termination with escalation, and a port wait
- [x] 1.2 Deduplicate the resolved PIDs so a process that is both recorded and holding the port is acted on once

## 2. stop.sh

- [x] 2.1 Stop the recorded process, its descendants, and the port holder
- [x] 2.2 Escalate to an unconditional kill after 20s for the backend and 5s for the frontend
- [x] 2.3 Re-check the port after stopping and warn when it is still held
- [x] 2.4 Report "not running" only when nothing is recorded and the port is free

## 3. start.sh

- [x] 3.1 Refuse to start when either port is occupied, naming the holding PID, before building
- [x] 3.2 Wait for each service to bind and report the PID that bound the port
- [x] 3.3 Report a failure to bind within 60s instead of a successful start

## 4. Verification

- [x] 4.1 Verify the helpers against the running app: a dead recorded frontend PID with a live port holder resolves to the port holder, and a live backend PID that also holds the port resolves once
- [x] 4.2 `bash -n` clean on all three scripts
- [x] 4.3 Full stop and start cycle leaves both ports bound by the newly started processes
- [x] 4.4 Confirm `start.sh` exits non-zero and starts nothing while a port is occupied
