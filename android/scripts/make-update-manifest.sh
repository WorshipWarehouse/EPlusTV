#!/usr/bin/env bash
# Produce a signed-update manifest for GameDay.
#
#   ./scripts/make-update-manifest.sh app/build/outputs/apk/release/app-release.apk \
#       https://downloads.example.test/gameday/app-release.apk 42
#
# The manifest carries versionCode/versionName from the APK, the APK's SHA-256 and the
# signing certificate digest, so the client can verify the download before installing it.
set -euo pipefail

cd "$(dirname "$0")/.."

APK="${1:-}"
URL="${2:-}"
VERSION_CODE="${3:-}"

if [[ -z "$APK" || -z "$URL" ]]; then
  echo "Usage: $0 <apk> <https-url> [versionCode]" >&2
  exit 1
fi
if [[ "$URL" != https://* ]]; then
  echo "Update URLs must use HTTPS" >&2
  exit 1
fi

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [[ -z "$SDK" ]]; then
  SDK="$(sed -n 's/^sdk.dir=//p' local.properties 2>/dev/null || true)"
fi
APKSIGNER="$(find "$SDK/build-tools" -name apksigner -type f | sort | tail -n 1)"
AAPT2="$(find "$SDK/build-tools" -name aapt2 -type f | sort | tail -n 1)"

BADGING="$("$AAPT2" dump badging "$APK" | head -n 1)"
PACKAGE="$(sed -n "s/.*package: name='\([^']*\)'.*/\1/p" <<<"$BADGING")"
CODE_FROM_APK="$(sed -n "s/.*versionCode='\([0-9]*\)'.*/\1/p" <<<"$BADGING")"
NAME="$(sed -n "s/.*versionName='\([^']*\)'.*/\1/p" <<<"$BADGING")"
CODE="${VERSION_CODE:-$CODE_FROM_APK}"

SHA="$(sha256sum "$APK" | cut -d' ' -f1)"
SIGNER="$(JSIGNER=1)"
SIGNER_DIGEST="$("$APKSIGNER" verify --print-certs "$APK" | sed -n 's/^Signer #1 certificate SHA-256 digest: //p' | head -n 1)"

MIN_SDK="$(sed -n "s/.*sdkVersion:'\([0-9]*\)'.*/\1/p" <<<"$BADGING")"

cat <<JSON
{
  "versionCode": ${CODE},
  "versionName": "${NAME}",
  "apkUrl": "${URL}",
  "sha256": "${SHA}",
  "signerSha256": "${SIGNER_DIGEST}",
  "minSdk": ${MIN_SDK:-25},
  "publishedAtMs": $(($(date +%s) * 1000)),
  "notes": "Signed GameDay ${NAME} for package ${PACKAGE}"
}
JSON
