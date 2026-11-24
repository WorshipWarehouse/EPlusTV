#!/usr/bin/env bash
# Install GameDay on a Fire TV / Android TV device over ADB.
#
#   ./scripts/install.sh                 # installs the newest debug APK
#   ./scripts/install.sh path/to/app.apk
#   ADB=/path/to/adb ./scripts/install.sh
set -euo pipefail

cd "$(dirname "$0")/.."

ADB="${ADB:-adb}"
APK="${1:-}"

if [[ -z "$APK" ]]; then
  APK="$(find app/build/outputs/apk -name '*.apk' -type f -newermt '1970-01-01' | sort | tail -n 1)"
fi

if [[ -z "$APK" || ! -f "$APK" ]]; then
  echo "No APK found. Run ./scripts/build.sh first." >&2
  exit 1
fi

echo "==> Waiting for a device"
"$ADB" start-server >/dev/null 2>&1 || true
"$ADB" wait-for-device

DEVICE="$("$ADB" get-serialno)"
echo "==> Installing $APK on $DEVICE"
"$ADB" install -r "$APK"

echo "==> Launching GameDay"
"$ADB" shell monkey -p com.gameday.tv -c android.intent.category.LEANBACK_LAUNCHER 1 >/dev/null 2>&1 || \
  "$ADB" shell am start -n com.gameday.tv/com.gameday.tv.ui.MainActivity

echo
echo "Remote controls:"
echo "  D-pad        move / scroll"
echo "  OK           select, play, hold to toggle a favorite"
echo "  Back         return to the sidebar, then Home, then exit"
echo "  Menu (player) audio tracks, subtitles, aspect ratio, info, retry"
