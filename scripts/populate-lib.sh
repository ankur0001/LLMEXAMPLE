#!/usr/bin/env bash
# Download every dependency, plugin, and test-time artifact into ./Lib (run once while online).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

mkdir -p Lib

echo "==> Resolving dependencies and plugins into Lib/ (requires network)"
mvn -s .mvn/settings.xml -B dependency:go-offline dependency:resolve-plugins || true

echo "==> Pre-fetching Surefire JUnit Platform provider (needed for offline tests)"
mvn -s .mvn/settings.xml -B org.apache.maven.plugins:maven-dependency-plugin:3.8.1:get \
  -Dartifact=org.apache.maven.surefire:surefire-junit-platform:3.5.2 \
  -Dtransitive=true

echo "==> Pre-fetching Maven lifecycle plugins (Maven 3.9+ needs install/deploy 3.1.4)"
bash scripts/sync-maven-plugins.sh

echo "==> Syncing Surefire/JUnit test artifacts into Lib/"
bash scripts/sync-surefire-lib.sh

echo "==> Building project with tests and installing module artifacts into Lib/"
mvn -s .mvn/settings.xml -B clean install

echo "==> Removing remote provenance markers (required for offline builds)"
find Lib -name '_remote.repositories' -delete 2>/dev/null || true

JAR_COUNT="$(find Lib -name '*.jar' 2>/dev/null | wc -l | tr -d ' ')"
echo "==> Done. Lib contains ${JAR_COUNT} jar file(s)."
echo "    Offline build: ./scripts/build-offline.sh clean install"
