# Design – Stock Screener

Gestaltung der App als FigCraft-Projekt: Tokens, Komponenten und die Screens.
Die Werte sind aus dem Code abgeleitet („Tinte & Messing"), nicht erfunden.

## Dateien

| Datei | Inhalt |
|---|---|
| `stock-screener.figcraft` | FigCraft-Dokument (3 Seiten: Tokens, Komponenten, Screens) |
| `screens/desktop-treffer.png` | Hauptansicht mit Trefferliste und Chart |
| `screens/desktop-start.png` | Leerzustand vor dem ersten Scan |
| `screens/dialog-einstellungen.png` | Scan-Ablauf-Dialog (Zahnrad) |
| `screens/tokens-farben.png` | Farbpalette |
| `screens/tokens-typografie.png` | Text-Stiles |

## Öffnen

```
open design/stock-screener.figcraft
```

Für die Anbindung per MCP (Agenten steuern FigCraft): siehe `AGENTS.md` bzw.
`figcraft-cli mcp --attach 7979` bei laufender App.

## Farben (AppColors, `ui/theme/AppTheme.kt`)

| Rolle | Wert | Einsatz |
|---|---|---|
| Tinte | `#0B1720` | Hintergrund, Bühne |
| Tinte Mittel / Tief | `#0A1822` / `#07121A` | Verlauf nach unten |
| Panel | `#0F202B` | Einstellungs-Spalte, Dialog |
| Panel Kante | `#1C3340` | Linien, Felder, Raster |
| Messing | `#D9A441` | alles Bedienbare: Buttons, Regler, Akzente |
| Auf Messing | `#1C1404` | Text auf Messing-Flächen |
| Text / Text gedimmt | `#E6EEF2` / `#7E97A3` | Fließtext / Nebeninfo |
| Grün / Rot | `#22C55E` / `#EF4444` | Kursbewegung, Kriterium erfüllt/offen |
| Warnung / Fehler | `#E0A458` / `#EF5350` | Hinweise, Fehlerzeilen |
| Container Tief / Hoch | `#0C1A24` / `#142C38` | Flächen-Hierarchie |
| Sekundär | `#16303D` | aktive/ausgewählte Tabellenzeile |

## Typografie

| Stil | Font | Größe | Einsatz |
|---|---|---|---|
| Title Large | Georgia Bold | 22/28 | „Rebound Screener" |
| Section | Inter SemiBold | 16/22 | Abschnittstitel |
| Label | Inter Regular | 13/18 | Regler-Beschriftungen |
| Body | Inter Regular | 14/20 | Fließtext, Button-Labels |
| Dim | Inter Regular | 12/16 | Nebeninfo |
| Metric | Inter Bold | 22/26 | Kennzahlen |
| Table Header | Inter SemiBold | 11/14 | Spaltenköpfe |
| Table Cell | Inter Regular | 12/16 | Tabellenzellen |
| Caption | Inter Regular | 9/12 | Kann/Muss am Umschalter |

## Layout-Maße (aus dem Code)

- Einstellungs-Spalte: 360 dp breit, Innenabstand 16 dp, Abstand 12 dp
- Tabelle: 20 Spalten in der Reihenfolge der App — Ticker, Aktienname, Kurs,
  Perf 30 T. %, 12M %, RSI, relVol, SMA20 %, SMA50 %, die 8 Kriterien (100 px,
  Kopf mit „☐ Ja"-Umschalter), Score, Tag, Links. Die App blendet die Spalten
  rechts ab (horizontaler Scroll), hier sind sie vollständig ausgebreitet.
- Regler: Bahn 4 px hoch, Griff 4×16 px, abgerundet 2 px
- Buttons: Höhe 48 dp, Radius 10 px
- Dialog: 460×300, Radius 16 px

## Screens

1. **Desktop · Treffer** – Einstellungen links, rechts Kennzahlen, Trefferfilter in einer
   Zeile, Tabelle mit allen 20 Spalten (Ja/Nein grün/rot, Kann/Muss-Köpfe,
   Sortier-Indikator), CSV-Aktionen, Chart mit Achsen, Navigation und Quellen-Links
2. **Desktop · Start** – Leerzustand mit Hinweis zum Universum und CSV-Import
3. **Dialog · Einstellungen** – Scan-Ablauf (Batch-Größe, Pause) hinter dem Zahnrad

## Aktualisieren

Farb-/Text-Stiles sind im Dokument angelegt (`Farbe/…`, `Typo/…`). Ändert sich das
Theme im Code, hier die betroffenen Swatches/Stiles anpassen und die Screens
exportieren (`file.export`, siehe `screens/`).
