package com.github.tkussauer.wta.complexity;

/**
 * Kennzahlen, aus denen die Komplexitaet einer Vorlage berechnet wird.
 * Der {@link #key()} wird in der Bewertungskonfiguration verwendet.
 */
public enum Metric {

    PAGES("seiten", "Seiten", "Seitenanzahl laut Dokumenteigenschaften, mindestens Anzahl manueller Seitenumbrüche + 1"),
    SECTIONS("abschnitte", "Abschnitte", "Abschnitte (unterschiedliche Seitenlayouts, Kopf-/Fußzeilen)"),
    PARAGRAPHS("absaetze", "Absätze", "Absätze in allen Bereichen"),
    TABLES("tabellen", "Tabellen", "Tabellen"),
    NESTED_TABLES("verschachtelte_tabellen", "Verschachtelte Tabellen", "Tabellen innerhalb von Tabellen"),
    IMAGES("bilder", "Bilder/Grafiken", "Bilder, Logos und Diagramme"),
    TEXT_BOXES("textfelder", "Textfelder/Formen", "Textfelder und frei positionierte Formen"),
    HEADERS_FOOTERS("kopf_fusszeilen", "Kopf-/Fußzeilen", "Kopf- und Fußzeilen mit Inhalt (je Variante)"),
    DATA_FIELDS("datenfelder", "Datenfelder", "Variable Daten: MERGEFIELD, DOCVARIABLE, DOCPROPERTY, REF, SET, ASK, FILLIN"),
    CONDITIONS("bedingungen", "Bedingungen", "Bedingte Inhalte: IF, COMPARE, SKIPIF, NEXTIF"),
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
