## ADDED Requirements

### Requirement: Running services are identified by port, not only by PID file
`start.sh` and `stop.sh` SHALL treat the listening TCP port as the authoritative signal of whether a service is running, using the PID file as a hint only. The backend uses port 8085 and the frontend port 3005.

This is required because the frontend's recorded PID is a wrapper: `npm run dev` spawns `next dev`, which spawns the `next-server` process that binds the port. A PID file therefore cannot answer whether the frontend is serving.

#### Scenario: Recorded PID is dead but the port is held
- **WHEN** the frontend PID file names a process that has exited while another process still holds port 3005
- **THEN** the service is treated as running and the process holding the port is the one acted on

#### Scenario: Recorded PID is alive and holds the port
- **WHEN** the recorded PID is alive and is also the process holding the port
- **THEN** it is acted on once, not twice

#### Scenario: Nothing recorded and the port is free
- **WHEN** no PID file exists and the port has no listener
- **THEN** the service is reported as not running

### Requirement: Stopping a service releases its port
`stop.sh` SHALL terminate the recorded process, its descendants, and any process holding the service's port. It SHALL request termination first and escalate to an unconditional kill for anything still alive after a timeout: 20 seconds for the backend so a graceful shutdown can complete, 5 seconds for the frontend. It SHALL remove the PID file, then re-check the port and warn when it is still held.

#### Scenario: Wrapper and its child both stop
- **WHEN** the frontend is stopped and its recorded wrapper has a child holding the port
- **THEN** both the wrapper and the child are terminated and port 3005 is free afterwards

#### Scenario: Port still held after stopping
- **WHEN** a process still holds the port after termination was attempted
- **THEN** a warning naming the holding PID is printed rather than reporting a clean stop

### Requirement: Starting refuses an occupied port and confirms the bind
`start.sh` SHALL check both ports before building or launching anything, and SHALL exit with an error naming the holding PID when either is occupied, directing the operator to `stop.sh`. After launching each service it SHALL wait for that service to bind its port, reporting the PID that bound it, and SHALL report a failure to bind within 60 seconds instead of reporting a successful start.

#### Scenario: Port already occupied
- **WHEN** `start.sh` runs while a process holds port 3005
- **THEN** it reports the holding PID, instructs the operator to run `stop.sh`, exits non-zero, and does not build or launch anything

#### Scenario: Service binds its port
- **WHEN** a launched service binds its port within the timeout
- **THEN** the script reports it as started along with the PID that bound the port

#### Scenario: Service fails to bind
- **WHEN** a launched service does not bind its port within 60 seconds
- **THEN** the script reports the failure and points at the service's log rather than reporting a successful start
