package com.github.tkussauer.wta.blocks;

import java.util.List;

/**
 * Die Absaetze einer Vorlage in Dokumentreihenfolge.
 */
public record TemplateText(String document, String format, List<Paragraph> paragraphs) {
}
