# Agenten-Hinweise

## Deploy: immer beide Ziele

Ein Deploy geht **immer an beide Ziele** – nie nur eines davon.

| Ziel | Befehl | Wer baut |
|---|---|---|
| Webserver im Heimnetz | `./deploy.sh` | lokal (`wasmJsBrowserDistribution`) |
| GitHub Pages | `git push origin main` | Workflow *Build and deploy web app* |
| Cloudflare-Worker (Daten-Proxy) | `git push origin main` | Workflow *Deploy stock data proxy*, nur bei Änderungen unter `worker/**` |

Reihenfolge: committen → `./deploy.sh` → `git push origin main`.

Der GitHub-Build dauert ca. 3–5 Minuten; erst danach ist die Pages-Seite
aktuell. Der Worker braucht bei reinen App-Änderungen kein Redeploy.

### Danach beide Seiten prüfen

Im Browser mit echtem Scan („Screener starten") durchklicken und auf
Treffer, Aktiennamen und Konsolenfehler achten:

- `http://192.168.178.25/stock-screener/`
- `https://luigialice.github.io/stock-screener-kmp/`

Bekannte, harmlose 404: `/favicon.ico` (die App hat kein Icon).

## Vor dem Deploy

`./test.sh` (bzw. `./gradlew :composeApp:desktopTest`) muss grün sein.
Neues Verhalten mit Tests absichern, bevor es deployed wird.
