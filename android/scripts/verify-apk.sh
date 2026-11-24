#!/usr/bin/env bash
# Verify a built APK: signature, package metadata, SHA-256 and dependency/ABI summary.
#
#   ./scripts/verify-apk.sh app/build/outputs/apk/release/app-release.apk
set -euo pipefail

cd "$(dirname "$0")/.."

APK="${1:-}"
if [[ -z "$APK" || ! -f "$APK" ]]; then
  echo "Usage: $0 path/to/app.apk" >&2
  exit 1
fi

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [[ -z "$SDK" ]]; then
  SDK="$(sed -n 's/^sdk.dir=//p' local.properties 2>/dev/null || true)"
fi
if [[ -z "$SDK" ]]; then
  echo "Set ANDROID_HOME or local.properties" >&2
  exit 1
fi

APKSIGNER="$(find "$SDK/build-tools" -name apksigner -type f | sort | tail -n 1)"
AAPT2="$(find "$SDK/build-tools" -name aapt2 -type f | sort | tail -n 1)"

echo "==> Package"
"$AAPT2" dump badging "$APK" | head -n 12

echo
echo "==> Signature"
"$APKSIGNER" verify --print-certs "$APK" | head -n 12

echo
echo "==> SHA-256"
sha256sum "$APK"

echo
echo "==> Size"
du -h "$APK"
unzip -l "$APK" | tail -n 1
