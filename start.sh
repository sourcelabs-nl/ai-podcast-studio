#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib-process.sh
source "$SCRIPT_DIR/lib-process.sh"

PID_FILE="$SCRIPT_DIR/.app.pid"
FRONTEND_PID_FILE="$SCRIPT_DIR/.frontend.pid"
JAR_FILE="$SCRIPT_DIR/target/ai-podcast-studio-0.0.1-SNAPSHOT.jar"
NPM="/Users/soudmaijer/.nvm/versions/node/v22.16.0/bin/npm"

# Refuse to start when a port is already held. Starting anyway produces a process that dies on
# EADDRINUSE while the script reports success, which is how a stale frontend kept serving old code.
assert_port_free() {
  local name="$1" port="$2" holder
  holder=$(listeners_on_port "$port")
  if [ -n "$holder" ]; then
    echo "$name port $port is already in use by PID $holder."
    echo "Run ./stop.sh first."
    exit 1
  fi
}

assert_port_free "Backend" "$BACKEND_PORT"
assert_port_free "Frontend" "$FRONTEND_PORT"

echo "Building ai-podcast-studio..."
cd "$SCRIPT_DIR"
./mvnw -q package -DskipTests

echo "Starting ai-podcast-studio..."
java --enable-native-access=ALL-UNNAMED -jar "$JAR_FILE" > /dev/null 2>&1 &
APP_PID=$!
echo "$APP_PID" > "$PID_FILE"

if wait_for_port "$BACKEND_PORT" 60; then
  echo "Application started (PID $APP_PID, port $BACKEND_PORT). Logs: app.log"
else
  echo "Application (PID $APP_PID) did not bind port $BACKEND_PORT within 60s. Check app.log."
fi

echo "Starting Next.js frontend on port $FRONTEND_PORT..."
cd "$SCRIPT_DIR/frontend"
PORT=$FRONTEND_PORT $NPM run dev > "$SCRIPT_DIR/frontend.log" 2>&1 &
FRONTEND_PID=$!
echo "$FRONTEND_PID" > "$FRONTEND_PID_FILE"

# The wrapper hands the port to a next-server grandchild, so report whoever actually bound it. The
# PID file keeps the wrapper: stop.sh kills it along with its descendants and the port holder.
if wait_for_port "$FRONTEND_PORT" 60; then
  echo "Frontend started (wrapper PID $FRONTEND_PID, serving PID $(listeners_on_port "$FRONTEND_PORT" | tr '\n' ' ')). Logs: frontend.log"
else
  echo "Frontend (PID $FRONTEND_PID) did not bind port $FRONTEND_PORT within 60s. Check frontend.log."
fi
