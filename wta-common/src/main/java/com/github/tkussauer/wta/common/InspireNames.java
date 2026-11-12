package com.github.tkussauer.wta.common;

import java.text.Normalizer;

/**
 * Gueltige Bezeichner fuer Quadient Inspire (Variablen, Bloecke).
 */
public final class InspireNames {

    private InspireNames() {
    }

    /**
     * Wandelt einen Text in einen Inspire-Bezeichner: Umlaute ausgeschrieben, Woerter in PascalCase,
     * nur Buchstaben und Ziffern, beginnend mit einem Buchstaben ({@code "Straße"} → {@code "Strasse"},
     * {@code "2. Zeile"} → {@code "F2Zeile"}).
     */
    public static String identifier(String raw) {
        String text = raw.replace("ä", "ae").replace("ö", "oe").replace("ü", "ue")
                .replace("Ä", "Ae").replace("Ö", "Oe").replace("Ü", "Ue").replace("ß", "ss");
        text = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        StringBuilder result = new StringBuilder();
        for (String word : text.split("[^A-Za-z0-9]+")) {
            if (!word.isEmpty()) {
                result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            }
        }
        if (!result.isEmpty() && Character.isDigit(result.charAt(0))) {
            result.insert(0, 'F');
        }
        return result.toString();
    }
}
