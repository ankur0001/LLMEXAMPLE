#!/usr/bin/env bash
# Copy a Maven artifact directory from ~/.m2/repository into ./Lib (no network).
set -euo pipefail

if [[ $# -lt 3 ]]; then
  echo "Usage: $0 <groupId-dots> <artifactId> <version>"
  echo "Example: $0 org.junit.platform junit-platform-commons 1.9.3"
  exit 1
fi

GROUP_PATH="$(printf '%s' "$1" | tr '.' '/')"
ARTIFACT="$2"
VERSION="$3"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="${HOME}/.m2/repository/${GROUP_PATH}/${ARTIFACT}/${VERSION}"
DEST="${ROOT}/Lib/${GROUP_PATH}/${ARTIFACT}/${VERSION}"

if [[ ! -d "$SRC" ]]; then
  echo "Source not found: $SRC"
  exit 1
fi

mkdir -p "$DEST"
cp -f "$SRC"/* "$DEST/" 2>/dev/null || true
echo "Copied ${1}:${ARTIFACT}:${VERSION} -> Lib/"
