#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PID_FILE="$SCRIPT_DIR/.app.pid"
FRONTEND_PID_FILE="$SCRIPT_DIR/.frontend.pid"

# Stop backend
if [ -f "$PID_FILE" ]; then
  APP_PID=$(cat "$PID_FILE")
  if kill -0 "$APP_PID" 2>/dev/null; then
    echo "Stopping ai-podcast-studio (PID $APP_PID)..."
    kill "$APP_PID"
    for i in $(seq 1 20); do
      if ! kill -0 "$APP_PID" 2>/dev/null; then
        break
      fi
      sleep 1
    done
    if kill -0 "$APP_PID" 2>/dev/null; then
      echo "Graceful shutdown timed out, force killing..."
      kill -9 "$APP_PID"
    fi
    echo "Application stopped."
  else
    echo "Backend process $APP_PID is not running."
  fi
  rm -f "$PID_FILE"
else
  echo "No backend PID file found."
fi

# Stop frontend
if [ -f "$FRONTEND_PID_FILE" ]; then
  FRONTEND_PID=$(cat "$FRONTEND_PID_FILE")
  if kill -0 "$FRONTEND_PID" 2>/dev/null; then
    echo "Stopping Next.js frontend (PID $FRONTEND_PID)..."
    kill "$FRONTEND_PID"
    for i in $(seq 1 5); do
      if ! kill -0 "$FRONTEND_PID" 2>/dev/null; then
        break
      fi
      sleep 1
    done
    if kill -0 "$FRONTEND_PID" 2>/dev/null; then
      kill -9 "$FRONTEND_PID"
    fi
    echo "Frontend stopped."
  else
    echo "Frontend process $FRONTEND_PID is not running."
  fi
  rm -f "$FRONTEND_PID_FILE"
else
  echo "No frontend PID file found."
fi
