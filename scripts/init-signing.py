#!/usr/bin/env python3
"""Create a local release signing key once. Never print passwords."""
from pathlib import Path
import os
import secrets
import shutil
import subprocess

root = Path(__file__).resolve().parent.parent
properties = root / "keystore.properties"
key = root / ".secrets" / "roadie-release.jks"
if properties.exists() or key.exists():
    raise SystemExit("Existing signing configuration found. Preserve it; no files changed.")
keytool = shutil.which("keytool")
if not keytool:
    raise SystemExit("Install JDK 17 and add its bin directory to PATH first.")
key.parent.mkdir(mode=0o700, exist_ok=True)
password = secrets.token_urlsafe(48)
env = dict(os.environ, ROADIE_SIGNING_PASSWORD=password)
subprocess.run([keytool, "-genkeypair", "-keystore", str(key), "-storetype", "JKS",
                "-alias", "roadie", "-keyalg", "RSA", "-keysize", "3072", "-validity", "10000",
                "-storepass:env", "ROADIE_SIGNING_PASSWORD", "-keypass:env", "ROADIE_SIGNING_PASSWORD",
                "-dname", "CN=Roadie Release, O=Roadie, C=KR"], env=env, check=True)
key.chmod(0o600)
fd = os.open(properties, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
with os.fdopen(fd, "w") as out:
    out.write(f"storeFile=.secrets/roadie-release.jks\nstorePassword={password}\nkeyAlias=roadie\nkeyPassword={password}\n")
print("Created ignored .secrets/roadie-release.jks and keystore.properties. Back up both securely.")
