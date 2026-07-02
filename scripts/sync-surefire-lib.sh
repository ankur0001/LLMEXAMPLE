#!/usr/bin/env bash
# JUnit Platform + Surefire artifacts needed for offline test runs.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
COPY="${ROOT}/scripts/copy-artifact-to-lib.sh"

ARTIFACTS=(
  "org.junit.platform junit-platform-commons 1.9.3"
  "org.junit.platform junit-platform-engine 1.9.3"
  "org.junit.platform junit-platform-launcher 1.9.3"
  "org.junit.platform junit-platform-commons 1.11.4"
  "org.junit.platform junit-platform-engine 1.11.4"
  "org.junit.platform junit-platform-launcher 1.11.4"
  "org.apache.maven.surefire surefire-junit-platform 3.5.2"
  "org.apache.maven.surefire common-java5 3.5.2"
  "org.apache.maven.surefire surefire-shared-utils 3.5.2"
  "org.apache.maven.surefire surefire-api 3.5.2"
  "org.apache.maven.surefire surefire-logger-api 3.5.2"
  "org.apache.maven.surefire surefire-extensions-api 3.5.2"
)

for spec in "${ARTIFACTS[@]}"; do
  read -r group artifact version <<< "$spec"
  GROUP_PATH="$(printf '%s' "$group" | tr '.' '/')"
  if [[ -d "${HOME}/.m2/repository/${GROUP_PATH}/${artifact}/${version}" ]]; then
    bash "$COPY" "$group" "$artifact" "$version" || true
  else
    echo "Skip (not in ~/.m2): ${group}:${artifact}:${version}"
  fi
done

# Offline builds use Lib as the local repo; drop remote provenance markers.
find "${ROOT}/Lib" -name '_remote.repositories' -delete 2>/dev/null || true

echo "Surefire/JUnit offline artifacts synced."
