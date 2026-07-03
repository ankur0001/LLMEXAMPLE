#!/usr/bin/env bash
# Pre-fetch Maven lifecycle plugins used by Maven 3.9+ (required for offline builds).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

SETTINGS="${ROOT}/.mvn/settings.xml"

PLUGINS=(
  "org.apache.maven.plugins:maven-install-plugin:3.1.4"
  "org.apache.maven.plugins:maven-deploy-plugin:3.1.4"
  "org.apache.maven.plugins:maven-clean-plugin:3.2.0"
  "org.apache.maven.plugins:maven-resources-plugin:3.3.1"
  "org.apache.maven.plugins:maven-jar-plugin:3.4.1"
  "org.apache.maven.plugins:maven-compiler-plugin:3.13.0"
  "org.apache.maven.plugins:maven-surefire-plugin:3.5.2"
)

echo "==> Downloading Maven lifecycle plugins into Lib/"
for artifact in "${PLUGINS[@]}"; do
  echo "    ${artifact}"
  mvn -s "$SETTINGS" -B org.apache.maven.plugins:maven-dependency-plugin:3.8.1:get \
    -Dartifact="${artifact}" -Dtransitive=true -q
done

echo "==> Removing remote provenance markers"
find "${ROOT}/Lib" -name '_remote.repositories' -delete 2>/dev/null || true
find "${ROOT}/Lib" -name '*.lastUpdated' -delete 2>/dev/null || true

echo "==> Maven lifecycle plugins synced to Lib/"
