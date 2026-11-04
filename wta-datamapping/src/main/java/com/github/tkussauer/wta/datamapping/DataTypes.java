package com.github.tkussauer.wta.datamapping;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Bezeichnungen und Heuristik fuer den Datentyp einer Variablen.
 */
final class DataTypes {

    static final String TEXT = "Text";
    static final String DATE = "Datum";
    static final String NUMBER = "Zahl";
    static final String BOOLEAN = "Ja/Nein";
    static final String CHOICE = "Auswahlliste";
    static final String IMAGE = "Bild";
    static final String GUESSED = " (vermutet)";

    private static final Pattern DATE_NAME = Pattern.compile("datum|date|geburtstag|frist|termin|stichtag");
    private static final Pattern NUMBER_NAME = Pattern.compile(
            "betrag|summe|preis|anzahl|menge|zins|prozent|saldo|gebuehr|gebühr|amount|price|total");

    private DataTypes() {
    }

    /** Leitet den Datentyp aus dem Namen ab, wenn kein Formatschalter ihn festlegt. */
    static String guess(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (DATE_NAME.matcher(lower).find()) {
            return DATE + GUESSED;
        }
        if (NUMBER_NAME.matcher(lower).find()) {
            return NUMBER + GUESSED;
        }
        return TEXT;
    }
}
