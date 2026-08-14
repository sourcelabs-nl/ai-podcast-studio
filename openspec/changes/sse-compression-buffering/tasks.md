## 1. Deliver event streams unbuffered

- [x] 1.1 Confirm the backend streams correctly by requesting the event stream directly on port 8085
- [x] 1.2 Confirm the same request through the dashboard returns nothing, and that `Accept-Encoding: identity` restores it, isolating compression as the cause
- [x] 1.3 Disable compression in `next.config.ts`
- [x] 1.4 Re-request the event stream through the dashboard with default browser encoding and confirm the heartbeat arrives immediately
- [ ] 1.5 Run a script preview from the UI and confirm the stage labels advance while the pipeline runs
