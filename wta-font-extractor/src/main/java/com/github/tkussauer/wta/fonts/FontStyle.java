package com.github.tkussauer.wta.fonts;

import java.util.Comparator;
import java.util.Objects;

/**
 * Ein effektiver Schriftstil, wie er in Quadient Inspire als Font/Textstil angelegt werden kann.
 *
 * @param fontName  Name der Schriftart
 * @param sizePt    Schriftgroesse in Punkt
 * @param bold      fett
 * @param italic    kursiv
 * @param underline unterstrichen
 * @param color     Schriftfarbe als RGB-Hexwert (z. B. {@code 1F3864}) oder {@link #AUTO_COLOR}
 */
public record FontStyle(String fontName, double sizePt, boolean bold, boolean italic, boolean underline, String color) {

    public static final String AUTO_COLOR = "Automatisch";

    public static final Comparator<FontStyle> ORDER = Comparator
            .comparing(FontStyle::fontName, String.CASE_INSENSITIVE_ORDER)
            .thenComparingDouble(FontStyle::sizePt)
            .thenComparing(FontStyle::bold)
            .thenComparing(FontStyle::italic)
            .thenComparing(FontStyle::underline)
            .thenComparing(FontStyle::color);

    public FontStyle {
        Objects.requireNonNull(fontName, "fontName");
        Objects.requireNonNull(color, "color");
    }

    /**
     * Schnitt der Schrift, wie er ueblicherweise in Font-Dateien benannt ist.
     */
    public String weight() {
        if (bold && italic) {
            return "Bold Italic";
        }
        if (bold) {
            return "Bold";
        }
        if (italic) {
            return "Italic";
        }
        return "Regular";
    }
}
