# Word Template Analyzer

Sammlung von Java-Anwendungen zur Analyse von Word-Vorlagen, z. B. zur Vorbereitung
einer Migration nach **Quadient Inspire**.

## Projektstruktur (Maven Multi-Modul)

| Modul                | Inhalt                                                                  |
|----------------------|-------------------------------------------------------------------------|
| `wta-common`         | Gemeinsame Hilfsklassen: Vorlagensuche im Verzeichnis, Excel-Ausgabe    |
| `wta-font-extractor` | **Anwendung 1** – Extraktion aller Schriftstile in eine Excel-Datei     |
| `wta-complexity`     | **Anwendung 2** – Komplexitätsbewertung in T-Shirt-Größen (S–XL)        |

Weitere Anwendungen werden als zusätzliche Module angelegt und im Parent-`pom.xml`
unter `<modules>` eingetragen.

Voraussetzungen: Java 17+, Maven 3.8+.

```bash
mvn clean package
```

Jede Anwendung liegt danach als ausführbares JAR unter `<modul>/target/<modul>.jar`.

## Anwendung 1: Schriftarten-Extraktion

Iteriert über alle Word-Vorlagen eines Verzeichnisses (inkl. Unterverzeichnisse) und
schreibt alle verwendeten Schriftstile in eine Excel-Datei.

```bash
java -jar wta-font-extractor/target/wta-font-extractor.jar <Verzeichnis> <Ausgabedatei.xlsx>
```

| Parameter / Option        | Bedeutung                                                       |
|---------------------------|-----------------------------------------------------------------|
| `<Verzeichnis>`           | Verzeichnis mit den Word-Vorlagen                               |
| `<Ausgabedatei.xlsx>`     | Name der Excel-Datei (`.xlsx` wird bei Bedarf ergänzt)          |
| `-n`, `--nicht-rekursiv`  | Unterverzeichnisse nicht durchsuchen                            |
| `-h`, `--hilfe`           | Hilfe anzeigen                                                  |

Unterstützte Formate: `docx`, `dotx`, `docm`, `dotm` sowie die alten Binärformate `doc`, `dot`.
Das Format wird am Dateiinhalt erkannt; Word-Sperrdateien (`~$…`) werden übersprungen.

### Inhalt der Excel-Datei

| Arbeitsblatt        | Inhalt                                                                                                         |
|---------------------|----------------------------------------------------------------------------------------------------------------|
| **Schriftarten**    | Alle unterschiedlichen Schriftstile über alle Vorlagen (Schriftart, Schnitt, Größe, fett, kursiv, unterstrichen, Farbe) mit Häufigkeit, Formatvorlagen und Dokumenten – Grundlage für die Anlage der Fonts/Textstile in Inspire |
| Schriftfamilien     | Benötigte Schriftarten mit den verwendeten Schnitten (Regular, Bold, Italic, Bold Italic) – z. B. für die Bereitstellung der Font-Dateien |
| Details             | Verwendung je Dokument, Bereich (Hauptdokument, Kopf-/Fußzeile, Fuß-/Endnote), Absatz-/Zeichenformat, direkte Formatierung, Textbeispiel |
| Formatvorlagen      | Definierte Absatz- und Zeichenformatvorlagen je Dokument mit effektiver Schrift und Kennzeichen „Verwendet“     |
| Fehler              | Nicht lesbare Dateien (nur vorhanden, wenn Fehler auftraten)                                                   |

### Ermittlung der Schriftstile

Für jeden Textabschnitt wird der **effektive** Stil ermittelt, wie Word ihn anzeigt:
Dokumentstandard → Absatzformatvorlage (inkl. Basisvorlagen) → Zeichenformatvorlage → direkte Formatierung.
Designschriften (z. B. „+Textkörper“) und Designfarben werden über das Dokumentdesign aufgelöst.
Textfelder werden einbezogen, Feldfunktionscodes in `.doc`-Dateien ignoriert.

Hinweise:
- Bei Designfarben mit Schattierung/Aufhellung kann der berechnete RGB-Wert je Kanal um 1
  vom Wert abweichen, den Word anzeigt.
- In `.doc`-Dateien enthalten Zeichenformatvorlagen nur ihre eigenen Eigenschaften
  (ohne Dokumentstandard).

## Anwendung 2: Komplexitätsbewertung (T-Shirt-Größen)

Misst für jede Word-Vorlage eines Verzeichnisses Kennzahlen, berechnet daraus Punkte
und ordnet die Vorlage einer T-Shirt-Größe zu – z. B. zur Aufwandsschätzung der Migration.

```bash
java -jar wta-complexity/target/wta-complexity.jar <Verzeichnis> <Ausgabedatei.xlsx>
```

| Parameter / Option               | Bedeutung                                                 |
|----------------------------------|-----------------------------------------------------------|
| `<Verzeichnis>`                  | Verzeichnis mit den Word-Vorlagen                         |
| `<Ausgabedatei.xlsx>`            | Name der Excel-Datei (`.xlsx` wird bei Bedarf ergänzt)    |
| `-k`, `--konfiguration <Datei>`  | eigenes Bewertungsschema (siehe unten)                    |
| `-n`, `--nicht-rekursiv`         | Unterverzeichnisse nicht durchsuchen                      |
| `-h`, `--hilfe`                  | Hilfe anzeigen                                            |

### Inhalt der Excel-Datei

| Arbeitsblatt         | Inhalt                                                                                                   |
|----------------------|----------------------------------------------------------------------------------------------------------|
| **Komplexität**      | Je Vorlage: Größe (farbig), Punkte, die drei Hauptkomplexitätstreiber, ggf. Anhebung durch Mindestgröße und alle Kennzahlen; sortiert von XL nach S |
| Übersicht            | Verteilung auf S/M/L/XL mit Punktebereich, Anzahl, Anteil, Ø Punkte, Ø Seiten und häufigsten Treibern   |
| Punkte               | Punkte je Kennzahl und Vorlage – zeigt, woraus sich die Einstufung zusammensetzt                        |
| Bewertungsschema     | Die verwendeten Gewichte und Mindestgrößen (zur Nachvollziehbarkeit)                                    |
| Fehler               | Nicht lesbare Dateien (nur vorhanden, wenn Fehler auftraten)                                            |

### Kennzahlen

Seiten, Abschnitte, Absätze, Tabellen, verschachtelte Tabellen, Bilder/Grafiken, Textfelder/Formen,
Kopf-/Fußzeilen, Feldfunktionen nach Art (Datenfelder wie `MERGEFIELD`/`DOCVARIABLE`, Bedingungen wie `IF`,
Einbindungen wie `INCLUDETEXT`, Formularfelder, sonstige Felder), maximale Feldverschachtelung,
Inhaltssteuerelemente, Listen, verwendete Formatvorlagen, Schriftstile (aus Anwendung 1), Fuß-/Endnoten,
Textmarken, eingebettete Objekte und VBA-Makros. Die Beschreibung jeder Kennzahl steht im Blatt
„Bewertungsschema“.

### Bewertung

`Punkte = Σ (Kennzahl × Gewicht)`. Die Größe ergibt sich aus den Schwellen:

| Größe | Einstufung   | Punkte (Standard) |
|-------|--------------|-------------------|
| S     | einfach      | < 20              |
| M     | mittel       | 20 bis < 50       |
| L     | komplex      | 50 bis < 100      |
| XL    | sehr komplex | ab 100            |

Zusätzlich heben **Mindestgrößen** die Einstufung an, sobald eine Kennzahl vorkommt
(Standard: Makros → mind. L, Einbindungen und verschachtelte Tabellen → mind. M).

### Eigenes Bewertungsschema

Die Standardwerte stehen in
[`komplexitaet-standard.properties`](wta-complexity/src/main/resources/com/github/tkussauer/wta/complexity/komplexitaet-standard.properties).
Eine eigene Datei muss nur die abweichenden Werte enthalten (UTF-8):

```properties
# Schwellen anheben
schwelle.M=30
schwelle.L=70
schwelle.XL=150
# Bedingungen stärker gewichten
gewicht.bedingungen=6
# Makros nicht mehr automatisch als L einstufen
mindestgroesse.makros=
```

```bash
java -jar wta-complexity/target/wta-complexity.jar -k mein-schema.properties <Verzeichnis> <Ausgabe.xlsx>
```

Die Standardgewichte sind ein Startpunkt. Empfehlung: einige Vorlagen manuell einstufen,
mit dem Ergebnis vergleichen und Gewichte/Schwellen entsprechend kalibrieren.

Hinweise:
- In `.doc`-Dateien sind Inhaltssteuerelemente nicht vorhanden; Textfelder/Formen werden über
  die frei positionierten Zeichnungsobjekte gezählt.
- Die Seitenzahl stammt aus den Dokumenteigenschaften (von Word beim Speichern gesetzt);
  fehlt sie, wird sie aus manuellen Seitenumbrüchen geschätzt.
