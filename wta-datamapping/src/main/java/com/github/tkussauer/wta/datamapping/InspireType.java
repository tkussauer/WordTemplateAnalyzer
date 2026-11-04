package com.github.tkussauer.wta.datamapping;

/**
 * Variablentypen in Quadient Inspire Designer, auf die die Word-Datentypen abgebildet werden.
 */
public enum InspireType {

    STRING("String", "Text", "Text, Nur-Text/Rich-Text, Auswahllisten, Eingabeaufforderungen"),
    INT("Int", "Ganzzahl", "Zahlen ohne Nachkommastellen (z. B. Anzahl, Menge)"),
    DOUBLE("Double", "Dezimalzahl", "Zahlen mit Nachkommastellen, Zahlformat \\#"),
    CURRENCY("Currency", "Geldbetrag", "Beträge, Preise, Summen"),
    DATE_TIME("DateTime", "Datum/Uhrzeit", "Datumsformat \\@, Datums-Steuerelemente"),
    BOOL("Bool", "Wahrheitswert", "Kontrollkästchen (Formularfeld, Inhaltssteuerelement)"),
    ARRAY("Array", "Wiederholende Struktur", "Wiederholungsabschnitte (Inhaltssteuerelement)");

    private final String inspireName;
    private final String meaning;
    private final String derivedFrom;

    InspireType(String inspireName, String meaning, String derivedFrom) {
        this.inspireName = inspireName;
        this.meaning = meaning;
        this.derivedFrom = derivedFrom;
    }

    /** Typname, wie er in Inspire Designer angezeigt wird. */
    public String inspireName() {
        return inspireName;
    }

    public String meaning() {
        return meaning;
    }

    public String derivedFrom() {
        return derivedFrom;
    }
}
