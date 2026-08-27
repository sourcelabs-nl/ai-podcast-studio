#!/usr/bin/env bash
#
# Creates the Nitter session token for this instance.
#
# Run this yourself in your own terminal. It prompts for the password with echo disabled, so the
# password never appears on screen, in your shell history, or in any agent transcript.
#
#   ./docker/nitter/create-session.sh
#
# The result is appended to docker/nitter/sessions.jsonl, which is git-ignored.
#
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORK="${TMPDIR:-/tmp}/nitter-session-tools"

echo "This logs a Twitter/X account in and stores its session for your local Nitter instance."
echo
echo "Strongly recommended: use a burner account, not your personal one. The account attached"
echo "to a Nitter instance gets rate-limited, locked behind captchas, and eventually suspended."
echo
read -r -p "Continue? [y/N] " ok
[[ "$ok" =~ ^[Yy]$ ]] || { echo "Aborted."; exit 0; }
echo

# --- tooling -----------------------------------------------------------------------------------
if [ ! -d "$WORK/nitter" ]; then
  echo "Fetching Nitter's session tools into $WORK ..."
  mkdir -p "$WORK"
  git clone --depth 1 --quiet https://github.com/zedeus/nitter "$WORK/nitter"
else
  echo "Updating session tools ..."
  git -C "$WORK/nitter" pull --quiet --ff-only || true
fi

if [ ! -d "$WORK/venv" ]; then
  echo "Creating a virtualenv (keeps these dependencies out of your system Python) ..."
  python3 -m venv "$WORK/venv"
fi
# shellcheck disable=SC1091
source "$WORK/venv/bin/activate"
echo "Installing dependencies ..."
pip install --quiet --upgrade pip
pip install --quiet -r "$WORK/nitter/tools/requirements.txt"
echo

# --- credentials -------------------------------------------------------------------------------
read -r -p "X username (without @): " USERNAME
read -r -s -p "X password (not echoed): " PASSWORD
echo
read -r -p "TOTP secret if 2FA is on, else press Enter: " TOTP
echo

[ -n "$USERNAME" ] || { echo "Username is required." >&2; exit 1; }
[ -n "$PASSWORD" ] || { echo "Password is required." >&2; exit 1; }

# --- login -----------------------------------------------------------------------------------
# A real browser window opens and drives the login. Do not close it; if X shows a captcha or an
# "unusual activity" check, solve it in that window and the script continues.
echo "Opening a browser to log in. Solve any captcha in that window if one appears."
echo

set +e
if [ -n "$TOTP" ]; then
  python3 "$WORK/nitter/tools/create_session_browser.py" \
    "$USERNAME" "$PASSWORD" "$TOTP" --append "$HERE/sessions.jsonl"
else
  python3 "$WORK/nitter/tools/create_session_browser.py" \
    "$USERNAME" "$PASSWORD" --append "$HERE/sessions.jsonl"
fi
status=$?
set -e
unset PASSWORD TOTP

if [ $status -ne 0 ]; then
  echo
  echo "Login failed. Common causes:" >&2
  echo "  - X demanded a captcha or email/phone confirmation; rerun and solve it in the browser" >&2
  echo "  - wrong password, or a TOTP secret that is the QR-code secret rather than the 6-digit code" >&2
  echo "  - the account is locked; unlock it at https://x.com and retry" >&2
  exit $status
fi

sessions=$(wc -l < "$HERE/sessions.jsonl" | tr -d ' ')
echo
echo "Session written. $HERE/sessions.jsonl now has $sessions session(s)."
echo
echo "Next:"
echo "  docker compose -f docker/nitter/docker-compose.yml restart nitter"
echo "  curl -s http://localhost:8081/${USERNAME}/rss | head -20"
echo "  ./scripts/repoint-nitter-sources.sh http://localhost:8081 --dry-run"
