#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ ! -f keystore.properties ]]; then
  echo 'Run python3 scripts/init-signing.py first.' >&2
  exit 1
fi
./gradlew :core:test :app:lintRelease :app:assembleRelease --console=plain
sdk_path="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [[ -z "$sdk_path" ]]; then
  echo 'Set ANDROID_HOME to verify the APK signature.' >&2
  exit 1
fi
apksigner_path="$sdk_path/build-tools/35.0.0/apksigner"
"$apksigner_path" verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk
mkdir -p apk
cp app/build/outputs/apk/release/app-release.apk apk/Roadie_Roadie_release.apk
shasum -a 256 apk/Roadie_Roadie_release.apk > apk/SHA256SUMS
python3 - "$apksigner_path" <<'PY'
import json
from pathlib import Path
import re
import subprocess
import sys
from datetime import datetime, timezone

certificate = subprocess.check_output([sys.argv[1], "verify", "--print-certs", "apk/Roadie_Roadie_release.apk"], text=True)
source = Path("app/build.gradle.kts").read_text()
info = {
    "applicationId": "ai.roadie.app",
    "versionName": re.search(r'versionName = "([^"]+)"', source).group(1),
    "sourceCommit": subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip(),
    "builtAtUtc": datetime.now(timezone.utc).isoformat(),
    "apk": "Roadie_Roadie_release.apk",
    "sha256": Path("apk/SHA256SUMS").read_text().split()[0],
    "signerCertificateSha256": re.search(r"certificate SHA-256 digest: ([a-f0-9]+)", certificate).group(1),
    "minSdk": 28,
    "targetSdk": 34,
}
Path("apk/BUILD_INFO.json").write_text(json.dumps(info, indent=2) + "\n")
PY
echo 'Packaged apk/Roadie_Roadie_release.apk'
