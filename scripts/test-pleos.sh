#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest --console=plain
adb_path="${ANDROID_HOME:?Set ANDROID_HOME}/platform-tools/adb"
adb_args=()
if [[ -n "${ANDROID_SERIAL:-}" ]]; then adb_args=(-s "$ANDROID_SERIAL"); fi
driver_user=$("$adb_path" "${adb_args[@]}" shell am get-current-user | tr -d '\r')
if [[ ! "$driver_user" =~ ^[0-9]+$ ]]; then echo 'Cannot identify current driver user.' >&2; exit 1; fi
"$adb_path" "${adb_args[@]}" install --no-streaming -r --user "$driver_user" app/build/outputs/apk/debug/app-debug.apk
"$adb_path" "${adb_args[@]}" install --no-streaming -r -t --user "$driver_user" app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
mkdir -p app/build/reports/pleos
"$adb_path" "${adb_args[@]}" shell am instrument --user "$driver_user" -w -r \
  ai.roadie.app.test/androidx.test.runner.AndroidJUnitRunner | tee app/build/reports/pleos/instrumentation.txt
if ! grep -Eq '^OK \([0-9]+ tests?\)' app/build/reports/pleos/instrumentation.txt; then
  echo 'Instrumentation failed. See app/build/reports/pleos/instrumentation.txt.' >&2
  exit 1
fi
echo "Pleos instrumentation passed for driver user $driver_user."
