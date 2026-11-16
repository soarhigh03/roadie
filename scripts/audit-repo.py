#!/usr/bin/env python3
"""Check tracked paths, authored text, and optional APK without printing secrets."""
from pathlib import Path
import argparse
import re
import subprocess
import zipfile

root = Path(__file__).resolve().parent.parent
parser = argparse.ArgumentParser()
parser.add_argument("--apk", type=Path)
args = parser.parse_args()
tracked = subprocess.check_output(["git", "ls-files", "-z"], cwd=root).decode().split("\0")
failures = []
secret_pattern = re.compile(rb"sk-[A-Za-z0-9_-]{24,}|-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----")
for name in filter(None, tracked):
    path = Path(name)
    if path.suffix in {".jks", ".keystore", ".p12"} or path.name in {".env", "keystore.properties"} or ".secrets" in path.parts:
        failures.append(f"Secret file is tracked: {name}")
    if path.suffix in {".kt", ".kts", ".js", ".mjs", ".md", ".json", ".xml", ".properties", ".sh", ".py"}:
        data = (root / name).read_bytes()
        if secret_pattern.search(data):
            failures.append(f"Potential secret in tracked text: {name}")

authored = [root / "README.md", *root.glob("docs/**/*.md"), *root.glob("app/src/main/java/**/*.kt"), *root.glob("core/src/main/**/*.kt")]
for path in authored:
    if path.exists() and any(c in path.read_text() for c in ("\u00b7", "\u2014")):
        failures.append(f"Prohibited punctuation: {path.relative_to(root)}")

if args.apk:
    private_values = set()
    for file, keys in [(root / "server/.env", {"ROADIE_ACCESS_TOKEN", "OPENAI_API_KEY", "KAKAO_REST_API_KEY"}),
                       (root / "keystore.properties", {"storePassword", "keyPassword"})]:
        if file.exists():
            for line in file.read_text().splitlines():
                key, sep, value = line.partition("=")
                if sep and key.strip() in keys and len(value.strip().strip("'\"")) >= 16:
                    private_values.add(value.strip().strip("'\"").encode())
    with zipfile.ZipFile(args.apk) as archive:
        for name in archive.namelist():
            content = archive.read(name)
            if any(value in content for value in private_values):
                failures.append(f"Local private value appears in APK entry: {name}")
if failures:
    raise SystemExit("\n".join(failures))
print("Tracked secrets, authored punctuation, and supplied APK checks passed.")
