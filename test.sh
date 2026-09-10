#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")"

if ./gradlew --version >/dev/null 2>&1; then
    GW="./gradlew"
else
    GW="gradle"
fi

exec $GW --no-daemon :composeApp:desktopTest "$@"
