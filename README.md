# Word Template Analyzer

Sammlung von Java-Anwendungen zur Analyse von Word-Vorlagen, z. B. zur Vorbereitung
einer Migration nach **Quadient Inspire**.

## Projektstruktur (Maven Multi-Modul)

| Modul                | Inhalt                                                                  |
|----------------------|-------------------------------------------------------------------------|
| `wta-common`         | Gemeinsame Hilfsklassen: Vorlagensuche im Verzeichnis, Excel-Ausgabe    |
| `wta-font-extractor` | **Anwendung 1** – Extraktion aller Schriftstile in eine Excel-Datei     |
| `wta-complexity`     | **Anwendung 2** – Komplexitätsbewertung in T-Shirt-Größen (S–XL)        |
| `wta-datamapping`    | **Anwendung 3** – Extraktion aller zu befüllenden Variablen (Datenmapping) |
| `wta-conditions`     | **Anwendung 4** – Extraktion aller Bedingungen (IF, COMPARE, SKIPIF, NEXTIF) |

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
| Felder & Bedingungen | Je Vorlage jedes Datenfeld und jede Bedingung mit Bereich (**Inhalt** oder **Kopf-/Fußzeile**), Feldart und Anzahl der Vorkommen |
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
- Seiten: Die Angabe in den Dokumenteigenschaften ist oft veraltet (Stand des letzten Speicherns).
  Verwendet wird daher das Maximum aus dieser Angabe und einer Schätzung über manuelle
  Seitenumbrüche, Abschnitte, die auf einer neuen Seite beginnen, und von Word vermerkte Umbrüche.
- Datenfelder und Bedingungen werden zusätzlich getrennt nach **Inhalt** (Haupttext, Tabellen,
  Textfelder, Fuß-/Endnoten) und **Kopf-/Fußzeile** ausgewiesen („Datenfelder (Inhalt)“,
  „Datenfelder (Kopf-/Fußzeile)“, „Bedingungen (Inhalt)“, „Bedingungen (Kopf-/Fußzeile)“). Ein Feld,
  das in beiden Bereichen vorkommt, zählt in beiden. Diese Kennzahlen haben standardmäßig das Gewicht 0,
  da sie in den Gesamtwerten enthalten sind. Für eine getrennte Gewichtung im eigenen Schema
  `gewicht.datenfelder` bzw. `gewicht.bedingungen` auf 0 setzen und z. B.
  `gewicht.datenfelder_inhalt` und `gewicht.datenfelder_kopf_fusszeile` vergeben.
- Datenfelder zählen je Feldname einmal (`MERGEFIELD Name` an fünf Stellen = 1 Datenfeld);
  alle Vorkommen stehen in der Kennzahl „Datenfeld-Verwendungen“.
- Kopf-/Fußzeilen, die Word nicht anzeigt (z. B. Variante „Erste Seite“, wenn der Abschnitt
  „Erste Seite anders“ nicht aktiviert hat), fließen nicht in die Bewertung ein; ihre Anzahl steht
  in „Nicht angezeigte Kopf-/Fußzeilen“. Das gilt auch für die Schriftarten-Extraktion; das
  Datenmapping führt ihre Variablen mit dem Bereich „… (nicht angezeigt)“ und einem Hinweis.

## Anwendung 3: Datenmapping (Variablen-Extraktion)

Extrahiert alle Variablen, die beim Erzeugen eines Dokuments befüllt werden müssen, und
fasst sie über alle Vorlagen zu einer Mapping-Liste zusammen.

```bash
java -jar wta-datamapping/target/wta-datamapping.jar <Verzeichnis> <Ausgabedatei.xlsx>
```

| Parameter / Option             | Bedeutung                                                              |
|--------------------------------|------------------------------------------------------------------------|
| `<Verzeichnis>`                | Verzeichnis mit den Word-Vorlagen                                      |
| `<Ausgabedatei.xlsx>`          | Name der Excel-Datei (`.xlsx` wird bei Bedarf ergänzt)                 |
| `-p`, `--platzhalter <Regex>`  | zusätzliches Platzhaltermuster, Gruppe 1 = Name (mehrfach möglich), z. B. `-p "%([A-Za-z_]+)%"` |
| `--ohne-textmarken`            | Textmarken nicht als Variablen aufnehmen                               |
| `-n`, `--nicht-rekursiv`       | Unterverzeichnisse nicht durchsuchen                                   |
| `-h`, `--hilfe`                | Hilfe anzeigen                                                         |

### Erkannte Variablen

| Kategorie            | Quelle                                                                                   |
|----------------------|------------------------------------------------------------------------------------------|
| Seriendruckfeld      | `MERGEFIELD`; Datentyp/Format aus den Schaltern `\@` (Datum) und `\#` (Zahl)           |
| Dokumentvariable     | `DOCVARIABLE` sowie in der Vorlage gespeicherte Dokumentvariablen (nur docx-Familie)    |
| Dokumenteigenschaft  | `DOCPROPERTY` sowie benutzerdefinierte Dokumenteigenschaften                             |
| Eingabeaufforderung  | `ASK`, `FILLIN`                                                                           |
| Formularfeld         | `FORMTEXT`, `FORMCHECKBOX`, `FORMDROPDOWN` mit Name, Standardwert und Auswahlwerten      |
| Inhaltssteuerelement | Tag bzw. Titel, Typ (Text, Datum, Dropdown, Kontrollkästchen, Bild, …), Auswahlwerte und XML-Datenbindung (XPath); Bausteine wie Seitenzahlen oder Deckblätter werden ignoriert |
| Textmarke            | benannte Textmarken sowie `REF` und `SET` (ohne versteckte wie `_GoBack`)                |
| Platzhalter          | Text wie `«Name»`, `{{Name}}`, `${Name}`, `<<Name>>`, `[[Name]]` außerhalb von Feldern   |

Variablen innerhalb von `IF`-Feldern (in der Bedingung oder im bedingten Text) werden gekennzeichnet;
die Bedingung steht im Blatt „Vorkommen“. Feldergebnisse wie `«Name»` eines `MERGEFIELD` zählen nicht
zusätzlich als Platzhalter. Gleichnamige Variablen werden ohne Beachtung der Groß-/Kleinschreibung
zusammengefasst, wie Word es bei Feldnamen tut.

### Inhalt der Excel-Datei

| Arbeitsblatt   | Inhalt                                                                                              |
|----------------|-----------------------------------------------------------------------------------------------------|
| **DataModel**  | Vorschlag für ein konsolidiertes, dublettenfreies Datenmodell für Quadient Inspire (siehe unten)     |
| **Variablen**  | Jede Variable einmal: Kategorie, Feldarten, Datentyp, Format, Auswahlwerte, Standardwert, Datenbindung, „Innerhalb IF-Feld“, „Im Text verwendet“, Häufigkeit, Vorlagen, Beispielkontext – plus die leeren (gelb markierten) Spalten **Ziel-Datenfeld**, **Datenquelle** und **Bemerkung** zum Ausfüllen des Mappings |
| Vorkommen      | Jedes einzelne Vorkommen mit Bereich (Hauptdokument, Kopf-/Fußzeile, …), Feldcode, Bedingung und Textkontext |
| Vorlagen       | Anzahl Variablen je Vorlage und Kategorie                                                           |
| Fehler         | Nicht lesbare Dateien (nur vorhanden, wenn Fehler auftraten)                                        |

### Arbeitsblatt „DataModel“ – Datenmodell-Vorschlag für Quadient Inspire

Fasst alle Word-Variablen zu einem Datenmodell zusammen, das direkt als Datenstruktur in Inspire
Designer angelegt werden kann:

- **Gültige Inspire-Bezeichner:** Feldnamen in PascalCase ohne Umlaute, Leer- und Sonderzeichen
  (`Straße` → `Strasse`, `Geburts datum` → `GeburtsDatum`).
- **Ohne Dubletten:** Variablen mit gleichem Bezeichner werden über Kategorien und Vorlagen hinweg
  zusammengeführt, z. B. `MERGEFIELD Strasse`, `«Straße»` und das Inhaltssteuerelement `strasse`.
  Die Spalte „Herkunft (Word)“ zeigt, woraus ein Feld entstanden ist.
- **Struktur:** Hierarchien aus Namen (`kunde.anschrift.ort`) oder XML-Datenbindungen
  (`/vertrag/nummer`) werden übernommen. Sonst wird anhand des Namens eine fachliche Gruppe
  vorgeschlagen (Empfaenger, Adresse, Kontakt, Vertrag, Rechnung, Absender, Allgemein);
  Dokumenteigenschaften und -variablen kommen in die Gruppe „Dokument“. Pfad: `Data.<Gruppe>.<Feld>`.
- **Inspire-Datentypen:** String, Int, Double, Currency, DateTime, Bool, Array, abgeleitet aus
  Formatschaltern, Steuerelementtypen und Namen (Beträge → Currency). Bei widersprüchlichen Typen
  wird ein Typ vorgeschlagen und ein Hinweis gesetzt.
- **Hinweise zur Prüfung:** vermutete Datentypen, berechnete Werte (`SET`), reine Textmarken,
  nur definierte Variablen, gleiche oder ähnliche Feldnamen in anderen Gruppen.

Darstellung: Kennzahlen-Kacheln, formatierte Excel-Tabelle mit Filter, farbige Datentypen,
Datenbalken für die Abdeckung (Anteil der Vorlagen), Trennlinien je Gruppe, fixierte Kopfzeile und
Feldspalten, Auswahlliste „Übernehmen“ (Ja/Nein) und Spalte „Bemerkung“ für die fachliche
Abstimmung sowie eine Legende der Datentypen. Das Blatt ist für den Querformat-Druck eingerichtet.

Hinweise:
- Der Datentyp wird aus Formatschaltern und Steuerelementtypen abgeleitet; ohne solche Angaben wird er
  aus dem Namen geschätzt (z. B. „…datum“ → „Datum (vermutet)“), sonst „Text“.
- In `.doc`-Dateien werden gespeicherte Dokumentvariablen nicht ausgelesen (nur `DOCVARIABLE`-Felder);
  Formularfelder erhalten ihren Namen über die gleichnamige Textmarke am Feldbeginn.
- Variablen, die nur per VBA-Makro befüllt werden, ohne ein Feld, eine Textmarke oder einen Platzhalter
  zu verwenden, können nicht erkannt werden.

## Anwendung 4: Bedingungen

Extrahiert alle Bedingungsfelder aus einer Vorlage oder allen Vorlagen eines Verzeichnisses,
zerlegt sie und beschreibt sie im Klartext – als Grundlage für die Umsetzung der Logik in Inspire.

```bash
java -jar wta-conditions/target/wta-conditions.jar <Datei oder Verzeichnis> <Ausgabedatei.xlsx>
```

| Parameter / Option        | Bedeutung                                                  |
|---------------------------|------------------------------------------------------------|
| `<Datei oder Verzeichnis>`| eine Word-Vorlage oder ein Verzeichnis mit Vorlagen        |
| `<Ausgabedatei.xlsx>`     | Name der Excel-Datei (`.xlsx` wird bei Bedarf ergänzt)     |
| `-n`, `--nicht-rekursiv`  | Unterverzeichnisse nicht durchsuchen                       |
| `-h`, `--hilfe`           | Hilfe anzeigen                                             |

Erkannt werden `IF` (mit Dann- und Sonst-Text), `COMPARE`, `SKIPIF` und `NEXTIF`, auch verschachtelt
und in Kopf-/Fußzeilen, Fuß- und Endnoten. Verschachtelte Bedingungen werden hierarchisch nummeriert
(`1`, `1.1`, `1.2`, …) und mit ihrer Lage gekennzeichnet („Dann-Zweig von 1“).

| Arbeitsblatt     | Inhalt                                                                                         |
|------------------|------------------------------------------------------------------------------------------------|
| **Bedingungen**  | Je Bedingung: Bereich, Nr., Ebene, Lage, Art, **Klartext** (z. B. `WENN «Anrede» ist gleich „Herr“ DANN „Sehr geehrter Herr «Name»“ SONST „Sehr geehrte Damen und Herren“`, je Ebene eingerückt), abgefragte Felder, Operator, Vergleichswert, Dann/Sonst, **normalisierter Ausdruck** (z. B. `Anrede == "Herr"`, `Zusatz != ""`), Felder im Dann/Sonst-Text, Hinweise, aktuelles Ergebnis, Kontext und vollständiger Feldcode |
| Felder           | Welche Datenfelder in Bedingungen abgefragt werden, mit Operatoren und vorkommenden Vergleichswerten (z. B. alle Werte von `Anrede`) |
| Übersicht        | Je Vorlage: Anzahl Bedingungen, davon verschachtelt, maximale Tiefe, abgefragte Felder, Anzahl in nicht angezeigten Bereichen |
| Fehler           | Nicht lesbare Dateien (nur vorhanden, wenn Fehler auftraten)                                   |

Hinweise:
- Bedingungen in Kopf-/Fußzeilen, die Word nicht anzeigt, werden grau und kursiv dargestellt.
- Vergleiche mit `*` oder `?` sind in Word Mustervergleiche; sie werden als Hinweis markiert.
- Der normalisierte Ausdruck ist eine Vorlage für die Umsetzung, keine fertige Inspire-Syntax.
- Sowohl `.docx`-Familie als auch `.doc`/`.dot` werden unterstützt.
