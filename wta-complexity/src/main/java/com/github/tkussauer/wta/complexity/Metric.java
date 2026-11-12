package com.github.tkussauer.wta.complexity;

/**
 * Kennzahlen, aus denen die Komplexitaet einer Vorlage berechnet wird.
 * Der {@link #key()} wird in der Bewertungskonfiguration verwendet.
 */
public enum Metric {

    PAGES("seiten", "Seiten", "Seitenanzahl: Maximum aus Dokumenteigenschaften und Schätzung über "
            + "Seitenumbrüche sowie Abschnitte, die auf einer neuen Seite beginnen"),
    SECTIONS("abschnitte", "Abschnitte", "Abschnitte (unterschiedliche Seitenlayouts, Kopf-/Fußzeilen)"),
    PARAGRAPHS("absaetze", "Absätze", "Absätze in allen Bereichen"),
    TABLES("tabellen", "Tabellen", "Tabellen"),
    NESTED_TABLES("verschachtelte_tabellen", "Verschachtelte Tabellen", "Tabellen innerhalb von Tabellen"),
    IMAGES("bilder", "Bilder/Grafiken", "Bilder, Logos und Diagramme"),
    TEXT_BOXES("textfelder", "Textfelder/Formen", "Textfelder und frei positionierte Formen"),
    HEADERS_FOOTERS("kopf_fusszeilen", "Kopf-/Fußzeilen", "Angezeigte Kopf- und Fußzeilen mit Inhalt (je Variante)"),
    HIDDEN_HEADERS_FOOTERS("ausgeblendete_kopf_fusszeilen", "Nicht angezeigte Kopf-/Fußzeilen",
            "Kopf-/Fußzeilen, die Word nicht anzeigt (z. B. Variante „Erste Seite“ ohne „Erste Seite anders“); "
            + "werden nicht weiter ausgewertet"),
    DATA_FIELDS("datenfelder", "Datenfelder", "Unterschiedliche variable Daten: MERGEFIELD, DOCVARIABLE, DOCPROPERTY, "
            + "REF, SET, ASK, FILLIN (jeder Feldname einmal)"),
    DATA_FIELDS_CONTENT("datenfelder_inhalt", "Datenfelder (Inhalt)",
            "Unterschiedliche Datenfelder im Inhalt (Haupttext, Fuß-/Endnoten)"),
    DATA_FIELDS_HEADER_FOOTER("datenfelder_kopf_fusszeile", "Datenfelder (Kopf-/Fußzeile)",
            "Unterschiedliche Datenfelder in angezeigten Kopf-/Fußzeilen"),
    DATA_FIELD_USES("datenfeld_verwendungen", "Datenfeld-Verwendungen", "Anzahl aller Vorkommen von Datenfeldern, "
            + "inkl. Wiederholungen"),
    CONDITIONS("bedingungen", "Bedingungen", "Bedingte Inhalte: IF, COMPARE, SKIPIF, NEXTIF"),
    CONDITIONS_CONTENT("bedingungen_inhalt", "Bedingungen (Inhalt)",
            "Bedingungen im Inhalt (Haupttext, Fuß-/Endnoten)"),
    CONDITIONS_HEADER_FOOTER("bedingungen_kopf_fusszeile", "Bedingungen (Kopf-/Fußzeile)",
            "Bedingungen in angezeigten Kopf-/Fußzeilen"),
    INCLUDES("einbindungen", "Einbindungen", "Externe Inhalte: INCLUDETEXT, INCLUDEPICTURE, AUTOTEXT, LINK, DATABASE"),
    FORM_FIELDS("formularfelder", "Formularfelder", "Legacy-Formularfelder: FORMTEXT, FORMCHECKBOX, FORMDROPDOWN"),
    OTHER_FIELDS("sonstige_felder", "Sonstige Felder", "Übrige Feldfunktionen, z. B. PAGE, NUMPAGES, DATE, HYPERLINK"),
    FIELD_NESTING("feldverschachtelung", "Max. Feldverschachtelung", "Maximale Verschachtelungstiefe von Feldern (1 = keine Verschachtelung)"),
    CONTENT_CONTROLS("inhaltssteuerelemente", "Inhaltssteuerelemente", "Inhaltssteuerelemente (nur docx/dotx)"),
    LISTS("listen", "Listen", "Unterschiedliche Nummerierungen/Aufzählungen"),
    STYLES("formatvorlagen", "Formatvorlagen", "Im Text verwendete Absatz- und Zeichenformatvorlagen"),
    FONT_STYLES("schriftstile", "Schriftstile", "Unterschiedliche Schriftstile (Schriftart, Größe, Schnitt, Farbe)"),
    NOTES("fussnoten", "Fuß-/Endnoten", "Fuß- und Endnoten"),
    BOOKMARKS("textmarken", "Textmarken", "Benannte Textmarken (ohne versteckte Textmarken wie _GoBack)"),
    EMBEDDED_OBJECTS("eingebettete_objekte", "Eingebettete Objekte", "Eingebettete OLE-Objekte (z. B. Excel-Tabellen)"),
    MACROS("makros", "Makros (VBA)", "1, wenn die Vorlage VBA-Makros enthält");

    private final String key;
    private final String label;
    private final String description;

    Metric(String key, String label, String description) {
        this.key = key;
        this.label = label;
        this.description = description;
    }

    public String key() {
        return key;
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }
}
