package com.github.tkussauer.wta.complexity;

import com.github.tkussauer.wta.fonts.DocumentFonts;
import com.github.tkussauer.wta.fonts.FontUsage;

import java.util.HashSet;
import java.util.Set;

/**
 * Leitet Kennzahlen aus der Schriftanalyse (Anwendung 1) ab.
 */
final class FontMetrics {

    private FontMetrics() {
    }

    static void apply(DocumentFonts fonts, TemplateMetrics metrics) {
        Set<Object> styles = new HashSet<>();
        Set<String> formatStyles = new HashSet<>();
        for (FontUsage usage : fonts.usages()) {
            styles.add(usage.style());
            formatStyles.add("P:" + usage.paragraphStyle());
            if (usage.characterStyle() != null) {
                formatStyles.add("C:" + usage.characterStyle());
            }
        }
        metrics.set(Metric.FONT_STYLES, styles.size());
        metrics.set(Metric.STYLES, formatStyles.size());
    }
}
