package com.github.tkussauer.wta.blocks;

/**
 * Ein Absatz einer Vorlage mit normalisiertem Text; Felder sind als Platzhalter enthalten
 * ({@code «Name»} fuer Datenfelder, {@code {CODE}} fuer andere Felder).
 *
 * @param number  laufende Nummer des Absatzes in der Vorlage (ab 1, nur Absaetze mit Text)
 * @param area    Bereich: Inhalt, Kopfzeile, Fußzeile oder Kopf-/Fußzeile
 * @param text    normalisierter Text (Leerraum zusammengefasst)
 */
public record Paragraph(int number, String area, String text) {

    /** Anzahl Buchstaben und Ziffern, Mass fuer den Umfang. */
    public int letters() {
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            if (Character.isLetterOrDigit(text.charAt(i))) {
                count++;
            }
        }
        return count;
    }
}
