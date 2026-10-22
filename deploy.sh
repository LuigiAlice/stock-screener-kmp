#!/bin/bash
# Baut die Wasm-Web-App und deployt sie auf den Webserver im Heimnetz.
set -euo pipefail
cd "$(dirname "$0")"

HOST="${DEPLOY_HOST:-192.168.178.25}"
REMOTE_DIR="${DEPLOY_DIR:-/var/www/html/stock-screener}"

echo "==> Baue Wasm-Distribution …"
./gradlew :composeApp:wasmJsBrowserDistribution

DIST="composeApp/build/dist/wasmJs/productionExecutable"
if [ ! -f "$DIST/index.html" ]; then
    echo "Build-Output fehlt: $DIST/index.html" >&2
    exit 1
fi

echo "==> Kopiere Apache-Config (idempotent) …"
scp deploy/apache.conf "root@$HOST:/etc/apache2/conf-enabled/stock-screener.conf"
ssh "root@$HOST" "a2enmod proxy proxy_http headers ssl >/dev/null 2>&1 || true; apache2ctl configtest && systemctl reload apache2"

echo "==> Deploye App nach root@$HOST:$REMOTE_DIR …"
ssh "root@$HOST" "mkdir -p '$REMOTE_DIR'"
rsync -av --delete "$DIST/" "root@$HOST:$REMOTE_DIR/"

echo "==> Fertig: http://$HOST/stock-screener/"
