## Why

`stop.sh` could report success while leaving the frontend running, and `start.sh` could report success while starting nothing.

`npm run dev` spawns `next dev`, which spawns the `next-server` process that actually binds the port. `start.sh` recorded the wrapper's PID, so `stop.sh` killed the wrapper and left the grandchild holding port 3005. The next `start.sh` then launched a frontend that died on `EADDRINUSE` while printing "Frontend started", leaving the previous server, and therefore the previous code, serving the dashboard.

Observed on the running app: `.frontend.pid` recorded 22252, which was dead, while PID 21869 held port 3005. `frontend.log` had accumulated three bind failures. A code change verified as merged was not necessarily the code being served, and the scripts gave no signal of it.

The backend is a single `java` process, so its PID file is usually accurate, but it fails the same way if the file is lost or stale while the process still holds port 8085.

## What Changes

- The port is the source of truth for what is running; the PID file is a hint.
- `stop.sh` stops the recorded process, its descendants, and whatever holds the port, then verifies the port is actually free and warns when it is not.
- `start.sh` refuses to start when a port is already held, naming the process that holds it, instead of launching a process that cannot bind.
- `start.sh` waits for each port to accept a listener and reports the PID that actually bound it, so a service that fails to come up is reported as such rather than as started.
- Shared helpers live in `lib-process.sh`, sourced by both scripts.

## Capabilities

### New Capabilities

- `app-lifecycle-scripts`: how `start.sh` and `stop.sh` identify, start, and stop the backend and frontend.

### Modified Capabilities

None.

## Impact

- `start.sh`, `stop.sh`, and the new `lib-process.sh`.
- Resolution is by listening TCP port (8085 backend, 3005 frontend) via `lsof`, so a foreign process on either port is reported by `start.sh` and stopped by `stop.sh`. These ports belong to this application by convention.
- No application code, API contract, schema, or cost change.
