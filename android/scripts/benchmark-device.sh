#!/usr/bin/env bash
# Measure GameDay on real hardware and write docs/PERFORMANCE_DEVICE.md.
#
#   ./scripts/benchmark-device.sh [package]
#
# Requires an ADB-connected device with GameDay installed. Nothing here runs on a build host.
set -euo pipefail

cd "$(dirname "$0")/.."

ADB="${ADB:-adb}"
PKG="${1:-com.gameday.tv}"
OUT="docs/PERFORMANCE_DEVICE.md"

"$ADB" wait-for-device
SERIAL="$("$ADB" get-serialno)"
MODEL="$("$ADB" shell getprop ro.product.model | tr -d '\r')"
SDK="$("$ADB" shell getprop ro.build.version.sdk | tr -d '\r')"
ABI="$("$ADB" shell getprop ro.product.cpu.abi | tr -d '\r')"
BUILD="$("$ADB" shell getprop ro.build.display.id | tr -d '\r')"

echo "==> Cold start"
"$ADB" shell am force-stop "$PKG"
START_MS="$("$ADB" shell am start -W -n "$PKG/com.gameday.tv.ui.MainActivity" \
  | sed -n 's/^TotalTime: //p' | tr -d '\r' | head -n 1)"

echo "==> Memory after startup (10 s settle)"
sleep 10
MEM_LINE="$("$ADB" shell dumpsys meminfo "$PKG" | grep -E "TOTAL PSS" | head -n 1 | tr -s ' ')"
MEM_JSON="$("$ADB" shell dumpsys meminfo "$PKG" | grep -E "TOTAL:" | head -n 1 | tr -s ' ')"

echo "==> Guide statistics from logcat"
"$ADB" logcat -d -s GameDay:V | tail -n 40 || true

{
  echo "# GameDay device performance"
  echo
  echo "Captured: $(date -u '+%Y-%m-%dT%H:%M:%SZ')"
  echo
  echo "| Field | Value |"
  echo "| --- | --- |"
  echo "| Serial | $SERIAL |"
  echo "| Model | $MODEL |"
  echo "| Build | $BUILD |"
  echo "| API level | $SDK |"
  echo "| Primary ABI | $ABI |"
  echo "| Cold start (TotalTime) | ${START_MS:-unavailable} ms |"
  echo "| Memory | ${MEM_LINE:-unavailable} |"
  echo "| Memory (detail) | ${MEM_JSON:-unavailable} |"
  echo
  echo "## Notes"
  echo
  echo "- Cold start includes Fire OS launcher overhead."
  echo "- Record guide size and refresh duration from Diagnostics in the app."
  echo "- Playback: press MENU in the player to read buffered position and track info."
} > "$OUT"

echo "==> Wrote $OUT"
