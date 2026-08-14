#!/usr/bin/env bash
# Shared process helpers for start.sh and stop.sh.
#
# A PID file alone cannot identify the frontend: `npm run dev` spawns `next dev`, which spawns the
# `next-server` process that actually holds the port. Killing the recorded wrapper leaves that
# grandchild listening, so the next start fails to bind while the PID file claims nothing is
# running. The port is therefore the source of truth and the PID file is only a hint.

BACKEND_PORT=8085
FRONTEND_PORT=3005

# PIDs listening on a TCP port, one per line, empty when the port is free.
listeners_on_port() {
  lsof -nP -tiTCP:"$1" -sTCP:LISTEN 2>/dev/null || true
}

# The PID recorded in a PID file, but only if that process is still alive.
live_pid_from_file() {
  local pid_file="$1"
  [ -f "$pid_file" ] || return 0
  local pid
  pid=$(cat "$pid_file" 2>/dev/null) || return 0
  [ -n "$pid" ] || return 0
  kill -0 "$pid" 2>/dev/null && echo "$pid"
  return 0
}

# Descendants of a PID, depth first, so a wrapper's children die with it.
descendants_of() {
  local parent="$1" child
  for child in $(pgrep -P "$parent" 2>/dev/null || true); do
    descendants_of "$child"
    echo "$child"
  done
}

# Every PID worth killing for one service: the recorded process, its descendants, and whatever
# holds the port. Deduplicated, because these sets overlap in the normal case.
pids_for_service() {
  local pid_file="$1" port="$2" recorded
  {
    recorded=$(live_pid_from_file "$pid_file")
    if [ -n "$recorded" ]; then
      echo "$recorded"
      descendants_of "$recorded"
    fi
    listeners_on_port "$port"
  } | awk 'NF && !seen[$0]++'
}

# Terminate PIDs gracefully, escalating to SIGKILL for anything still alive after the timeout.
terminate_pids() {
  local timeout="$1"; shift
  local pids=("$@") pid i alive

  for pid in "${pids[@]}"; do
    kill "$pid" 2>/dev/null || true
  done

  for ((i = 0; i < timeout; i++)); do
    alive=""
    for pid in "${pids[@]}"; do
      kill -0 "$pid" 2>/dev/null && alive="yes"
    done
    [ -z "$alive" ] && return 0
    sleep 1
  done

  for pid in "${pids[@]}"; do
    if kill -0 "$pid" 2>/dev/null; then
      echo "  PID $pid did not exit in ${timeout}s, force killing..."
      kill -9 "$pid" 2>/dev/null || true
    fi
  done
}

# Wait for a port to accept listeners, so callers can report the PID that actually bound it.
wait_for_port() {
  local port="$1" timeout="$2" i
  for ((i = 0; i < timeout; i++)); do
    [ -n "$(listeners_on_port "$port")" ] && return 0
    sleep 1
  done
  return 1
}
