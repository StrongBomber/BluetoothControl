#!/bin/sh
# Regenerate CHECKSUMS.sha256 from the APK at the repository root.
# Usage: ./scripts/gen_checksums.sh [path-to-apk]
set -eu

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APK="${1:-$ROOT/Bluetooth Klavye & Fare.apk}"

if [ ! -f "$APK" ]; then
    echo "error: APK not found: $APK" >&2
    exit 1
fi

# checksums entry must reference the file by its name as it lives in the repo root
NAME="$(basename "$APK")"
cd "$ROOT"
( cd "$ROOT" && sha256sum "$NAME" ) > CHECKSUMS.sha256

echo "wrote $ROOT/CHECKSUMS.sha256"
cat "$ROOT/CHECKSUMS.sha256"
