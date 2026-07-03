#!/usr/bin/env bash
# Offline build with absolute Lib path (works from any working directory).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

if [[ ! -d "${ROOT}/Lib/org" ]]; then
  echo "Lib/ is missing or empty. Run ./scripts/populate-lib.sh once while online."
  exit 1
fi

exec mvn \
  -s "${ROOT}/.mvn/settings-offline.xml" \
  -Dmaven.repo.local="${ROOT}/Lib" \
  -o \
  "$@"
