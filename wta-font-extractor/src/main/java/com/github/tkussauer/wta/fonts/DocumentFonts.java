package com.github.tkussauer.wta.fonts;

import java.util.List;

/**
 * Ergebnis der Schriftanalyse eines Dokuments.
 *
 * @param document  Dokumentname (relativ zum Eingabeverzeichnis)
 * @param usages    im Text verwendete Schriftstile
 * @param styles    definierte Formatvorlagen
 */
public record DocumentFonts(String document, List<FontUsage> usages, List<StyleDefinition> styles) {
}
