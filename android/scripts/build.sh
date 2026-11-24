#!/usr/bin/env bash
# Reproducible local build for the GameDay television app.
#
# Requires: JDK 17 and an Android SDK with platform 34 + build-tools 34.
# Set JAVA_HOME and ANDROID_HOME (or ANDROID_SDK_ROOT) before running, or pass them
# through android/local.properties (sdk.dir=...).
set -euo pipefail

cd "$(dirname "$0")/.."

GRADLE="${GRADLE:-./gradlew}"
if [[ ! -x "$GRADLE" ]]; then
  GRADLE="gradle"
fi

if [[ -z "${JAVA_HOME:-}" ]]; then
  echo "JAVA_HOME is not set; Gradle needs JDK 17." >&2
fi
if [[ -z "${ANDROID_HOME:-}" && -z "${ANDROID_SDK_ROOT:-}" && ! -f local.properties ]]; then
  echo "Set ANDROID_HOME or create android/local.properties with sdk.dir=..." >&2
  exit 1
fi

TASK="${1:-assembleDebug}"

echo "==> Running Gradle task: $TASK"
"$GRADLE" "$TASK" --console=plain

echo
echo "==> Build artifacts"
find app/build/outputs -name '*.apk' -type f -print 2>/dev/null | while read -r apk; do
  printf '%s  (%s)\n' "$apk" "$(du -h "$apk" | cut -f1)"
done
