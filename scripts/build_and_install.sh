#!/usr/bin/env bash

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

cd "$REPO_ROOT"

if ! command -v adb >/dev/null 2>&1; then
  echo "adb is not on PATH" >&2
  exit 1
fi

if ! ./gradlew :app:assembleDebug; then
  echo "Gradle build failed" >&2
  exit 1
fi

APK_PATH=$(find app/build/outputs/apk/debug -name '*-debug.apk' -print -quit)

if [[ -z "$APK_PATH" ]]; then
  echo "Debug APK not found" >&2
  exit 1
fi

if ! adb get-state >/dev/null 2>&1; then
  echo "No device connected" >&2
  exit 1
fi

echo "Installing $APK_PATH"
if install_output=$(adb install -r "$APK_PATH" 2>&1); then
  printf '%s\n' "$install_output"
  exit 0
fi

printf '%s\n' "$install_output"

if printf '%s' "$install_output" | grep -q "INSTALL_FAILED_UPDATE_INCOMPATIBLE"; then
  echo "Signature mismatch detected. Removing existing app and retrying install."
  adb uninstall dev.rex.app || true
  adb install "$APK_PATH"
else
  echo "Install failed." >&2
  exit 1
fi
