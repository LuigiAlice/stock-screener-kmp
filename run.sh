#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")"

if ./gradlew --version >/dev/null 2>&1; then
    GW="./gradlew"
else
    if command -v gradle >/dev/null; then
        GW="gradle"
    else
        echo "Weder Gradle-Wrapper noch Gradle gefunden." >&2
        exit 1
    fi
fi

exec $GW --no-daemon :composeApp:run
