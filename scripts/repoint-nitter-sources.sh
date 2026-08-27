#!/usr/bin/env bash
#
# Repoints every nitter.net source at a self-hosted Nitter instance.
#
# nitter.net was decommissioned upstream and answers 403, so the sources reading it are dead.
# This rewrites each source's URL through the app's REST API rather than touching the database.
# Updating a source through the API also clears its failure counters, so the repointed sources
# resume on their normal poll interval instead of their accumulated 24-hour backoff.
#
# Usage:
#   ./scripts/repoint-nitter-sources.sh http://localhost:8081      # nitter.net -> self-hosted
#   ./scripts/repoint-nitter-sources.sh http://localhost:8081 --dry-run
#
set -euo pipefail

API="${API_BASE_URL:-http://localhost:8085}"
NEW_BASE="${1:-}"
DRY_RUN="${2:-}"

if [ -z "$NEW_BASE" ]; then
  echo "Usage: $0 <new-nitter-base-url> [--dry-run]" >&2
  echo "  e.g. $0 http://localhost:8081" >&2
  exit 1
fi
NEW_BASE="${NEW_BASE%/}"

command -v jq >/dev/null 2>&1 || { echo "jq is required (brew install jq)" >&2; exit 1; }

# Discover the user and podcast rather than hardcoding ids, so this keeps working after a restore.
users=$(curl -sf -m 15 "$API/users") || { echo "Cannot reach the app at $API — is it running?" >&2; exit 1; }
user_id=$(echo "$users" | jq -r '.[0].id')
podcasts=$(curl -sf -m 15 "$API/users/$user_id/podcasts")
podcast_id=$(echo "$podcasts" | jq -r '.[0].id')

echo "App:     $API"
echo "User:    $user_id"
echo "Podcast: $(echo "$podcasts" | jq -r '.[0].name') ($podcast_id)"
echo "Target:  $NEW_BASE"
echo

sources=$(curl -sf -m 15 "$API/users/$user_id/podcasts/$podcast_id/sources")
targets=$(echo "$sources" | jq -c '[.[] | select(.url | test("://nitter\\.net/"))]')
count=$(echo "$targets" | jq 'length')

if [ "$count" -eq 0 ]; then
  echo "No nitter.net sources found — nothing to do."
  exit 0
fi

echo "Found $count nitter.net source(s)."
echo

updated=0
failed=0
while read -r source; do
  id=$(echo "$source" | jq -r '.id')
  old_url=$(echo "$source" | jq -r '.url')
  # Preserve the path (e.g. /sama/rss), swap only the scheme+host.
  path=$(echo "$old_url" | sed -E 's#^https?://nitter\.net##')
  new_url="${NEW_BASE}${path}"

  if [ "$DRY_RUN" = "--dry-run" ]; then
    echo "  would update $old_url -> $new_url"
    continue
  fi

  # Re-send every field: the update endpoint replaces the source's config wholesale, so omitting
  # a field would silently reset it to its default.
  body=$(echo "$source" | jq -c --arg url "$new_url" '{
    type, url: $url, pollIntervalMinutes, enabled, aggregate,
    maxFailures, maxBackoffHours, pollDelaySeconds, categoryFilter, label
  }')

  if curl -sf -m 20 -X PUT \
      -H 'Content-Type: application/json' \
      -d "$body" \
      "$API/users/$user_id/podcasts/$podcast_id/sources/$id" > /dev/null; then
    echo "  updated $old_url -> $new_url"
    updated=$((updated + 1))
  else
    echo "  FAILED  $old_url" >&2
    failed=$((failed + 1))
  fi
done < <(echo "$targets" | jq -c '.[]')

echo
if [ "$DRY_RUN" = "--dry-run" ]; then
  echo "Dry run — nothing changed."
else
  echo "Updated $updated source(s), $failed failure(s)."
  [ "$failed" -eq 0 ] || exit 1
fi
