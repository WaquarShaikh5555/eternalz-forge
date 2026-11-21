#!/usr/bin/env bash
set -uo pipefail
./gradlew --no-daemon connectedDebugAndroidTest 2>&1 | tee "$RUNNER_TEMP/device-tests.log"
status=${PIPESTATUS[0]}
mkdir -p design-screenshots
adb pull /sdcard/Android/data/dev.eternalz.forge/files/design-screenshots/. design-screenshots/ || true
exit "$status"
