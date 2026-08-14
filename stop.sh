#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib-process.sh
source "$SCRIPT_DIR/lib-process.sh"

PID_FILE="$SCRIPT_DIR/.app.pid"
FRONTEND_PID_FILE="$SCRIPT_DIR/.frontend.pid"

stop_service() {
  local name="$1" pid_file="$2" port="$3" timeout="$4"
  local pids

  # Read into an array so an empty result yields an empty array rather than one blank element.
  IFS=$'\n' read -r -d '' -a pids < <(pids_for_service "$pid_file" "$port" && printf '\0')

  if [ ${#pids[@]} -eq 0 ]; then
    echo "$name is not running (nothing recorded, port $port free)."
    rm -f "$pid_file"
    return 0
  fi

  echo "Stopping $name (PID ${pids[*]})..."
  terminate_pids "$timeout" "${pids[@]}"
  rm -f "$pid_file"

  local remaining
  remaining=$(listeners_on_port "$port")
  if [ -n "$remaining" ]; then
    echo "  Warning: port $port is still held by PID $remaining."
    return 1
  fi
  echo "$name stopped."
}

# The backend is a plain java process, so 20s covers a graceful Spring shutdown. The frontend is a
# wrapper plus its next-server child; both are killed together and exit quickly.
stop_service "ai-podcast-studio" "$PID_FILE" "$BACKEND_PORT" 20
stop_service "Next.js frontend" "$FRONTEND_PID_FILE" "$FRONTEND_PORT" 5
