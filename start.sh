#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PID_FILE="$SCRIPT_DIR/.app.pid"
FRONTEND_PID_FILE="$SCRIPT_DIR/.frontend.pid"
JAR_FILE="$SCRIPT_DIR/target/ai-podcast-studio-0.0.1-SNAPSHOT.jar"
NPM="/Users/soudmaijer/.nvm/versions/node/v22.16.0/bin/npm"

if [ -f "$PID_FILE" ] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
  echo "Application is already running (PID $(cat "$PID_FILE"))"
  exit 1
fi

echo "Building ai-podcast-studio..."
cd "$SCRIPT_DIR"
./mvnw -q package -DskipTests

echo "Starting ai-podcast-studio..."
java --enable-native-access=ALL-UNNAMED -jar "$JAR_FILE" > /dev/null 2>&1 &
APP_PID=$!
echo "$APP_PID" > "$PID_FILE"
echo "Application started (PID $APP_PID). Logs: app.log"

echo "Starting Next.js frontend on port 3005..."
cd "$SCRIPT_DIR/frontend"
PORT=3005 $NPM run dev > "$SCRIPT_DIR/frontend.log" 2>&1 &
FRONTEND_PID=$!
echo "$FRONTEND_PID" > "$FRONTEND_PID_FILE"
echo "Frontend started (PID $FRONTEND_PID). Logs: frontend.log"
