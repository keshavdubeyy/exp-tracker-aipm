#!/usr/bin/env bash
# Builds the tester APK ("fast": optimised, has the Sample data switch) and sends it to
# testers through Firebase App Distribution. They get an email with an install link.
#
#   ./scripts/distribute.sh "Added the + button and light mode"
#
# One-time setup (see TESTING.md):
#   firebase login
#   local.properties:  firebase.appId=1:1234567890:android:abc123
#                      firebase.testers=a@example.com,b@example.com   (and/or)
#                      firebase.groups=testers
set -euo pipefail
cd "$(dirname "$0")/.."

prop() { grep -E "^$1=" local.properties 2>/dev/null | head -1 | cut -d= -f2- || true; }

APP_ID="$(prop firebase.appId)"
TESTERS="$(prop firebase.testers)"
TESTER_GROUPS="$(prop firebase.groups)"
NOTES="${1:-}"

if [[ -z "$APP_ID" ]]; then
  echo "Missing firebase.appId in local.properties. Firebase console → Project settings → Your apps → App ID." >&2
  exit 1
fi
if [[ -z "$TESTERS" && -z "$TESTER_GROUPS" ]]; then
  echo "Add firebase.testers=email1,email2 or firebase.groups=testers to local.properties." >&2
  exit 1
fi
if [[ -z "$NOTES" ]]; then
  echo "Say what changed, for the testers' email:  ./scripts/distribute.sh \"What's new\"" >&2
  exit 1
fi
if ! command -v firebase >/dev/null; then
  echo "Firebase CLI not found: npm install -g firebase-tools" >&2
  exit 1
fi

# Minutes since 1 Jan 2026: always goes up, so each upload installs as an update.
VERSION_CODE=$(( ($(date +%s) - 1767225600) / 60 ))

export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home}"
./gradlew :app:assembleFast -PversionCode="$VERSION_CODE"

ARGS=(--app "$APP_ID" --release-notes "$NOTES")
[[ -n "$TESTERS" ]] && ARGS+=(--testers "$TESTERS")
[[ -n "$TESTER_GROUPS" ]] && ARGS+=(--groups "$TESTER_GROUPS")

firebase appdistribution:distribute app/build/outputs/apk/fast/app-fast.apk "${ARGS[@]}"
echo "Sent build $VERSION_CODE to testers."
