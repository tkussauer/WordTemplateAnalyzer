# Word Template Analyzer

Sammlung von Java-Anwendungen zur Analyse von Word-Vorlagen, z. B. zur Vorbereitung
einer Migration nach **Quadient Inspire**.

## Projektstruktur (Maven Multi-Modul)

| Modul                | Inhalt                                                                  |
|----------------------|-------------------------------------------------------------------------|
| `wta-common`         | Gemeinsame Hilfsklassen: Vorlagensuche im Verzeichnis, Excel-Ausgabe    |
| `wta-font-extractor` | **Anwendung 1** – Extraktion aller Schriftstile in eine Excel-Datei     |

Weitere Anwendungen werden als zusätzliche Module angelegt und im Parent-`pom.xml`
unter `<modules>` eingetragen.

Voraussetzungen: Java 17+, Maven 3.8+.

```bash
mvn clean package
```

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
