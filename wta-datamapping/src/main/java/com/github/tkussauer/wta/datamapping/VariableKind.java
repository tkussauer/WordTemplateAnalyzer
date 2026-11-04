package com.github.tkussauer.wta.datamapping;

/**
 * Herkunft einer Variablen in der Vorlage. Die Reihenfolge bestimmt die Sortierung im Ergebnis.
 */
public enum VariableKind {

    MERGE_FIELD("Seriendruckfeld"),
    DOC_VARIABLE("Dokumentvariable"),
    DOC_PROPERTY("Dokumenteigenschaft"),
    PROMPT("Eingabeaufforderung"),
    FORM_FIELD("Formularfeld"),
    CONTENT_CONTROL("Inhaltssteuerelement"),
    BOOKMARK("Textmarke"),
    PLACEHOLDER("Platzhalter");

    private final String label;

    VariableKind(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
