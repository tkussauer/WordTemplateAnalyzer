package com.github.tkussauer.wta.complexity;

/**
 * Bereich einer Vorlage, in dem ein Feld steht.
 */
public enum Region {

    /** Haupttext inkl. Tabellen, Textfeldern, Fuss- und Endnoten. */
    CONTENT("Inhalt"),
    /** Angezeigte Kopf- und Fusszeilen. */
    HEADER_FOOTER("Kopf-/Fußzeile");

    private final String label;

    Region(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
