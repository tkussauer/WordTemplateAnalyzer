package com.github.tkussauer.wta.complexity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocxMetricsExtractorTest {

    @TempDir
    Path tempDir;

    @Test
    void measuresComplexTemplate() throws Exception {
        Path file = tempDir.resolve("komplex.dotx");
        ComplexTemplates.writeComplex(file);

        TemplateMetrics metrics = new DocxMetricsExtractor().extract(file, "komplex.dotx");

        assertEquals("dotx", metrics.format());
        assertEquals(2, metrics.get(Metric.PAGES));
        assertEquals(2, metrics.get(Metric.TABLES));
        assertEquals(1, metrics.get(Metric.NESTED_TABLES));
        assertEquals(1, metrics.get(Metric.CONDITIONS));
        assertEquals(1, metrics.get(Metric.CONDITIONS_CONTENT));
        assertEquals(1, metrics.get(Metric.DATA_FIELDS));
        assertEquals(1, metrics.get(Metric.INCLUDES));
        assertEquals(1, metrics.get(Metric.OTHER_FIELDS));
        assertEquals(2, metrics.get(Metric.FIELD_NESTING));
        assertEquals(1, metrics.get(Metric.BOOKMARKS));
        assertEquals(1, metrics.get(Metric.HEADERS_FOOTERS));
        assertEquals(0, metrics.get(Metric.MACROS));
        assertTrue(metrics.get(Metric.FONT_STYLES) >= 1);
        assertTrue(metrics.get(Metric.PARAGRAPHS) >= 6);
    }

    @Test
    void ignoresHiddenHeadersAndCountsSectionPages() throws Exception {
        Path file = tempDir.resolve("abschnitte.docx");
        ComplexTemplates.writeWithHiddenHeader(file);

        TemplateMetrics metrics = new DocxMetricsExtractor().extract(file, "abschnitte.docx");

        // Abschnitt 2 beginnt auf neuer Seite, app.xml enthaelt keine Seitenzahl
        assertEquals(2, metrics.get(Metric.PAGES));
        assertEquals(1, metrics.get(Metric.HEADERS_FOOTERS));
        assertEquals(1, metrics.get(Metric.HIDDEN_HEADERS_FOOTERS));
        // Das IF-Feld steht nur in der nicht angezeigten Kopfzeile "Erste Seite"
        assertEquals(0, metrics.get(Metric.CONDITIONS));
        assertEquals(2, metrics.get(Metric.DATA_FIELDS));
        assertEquals(3, metrics.get(Metric.DATA_FIELD_USES));
        // Angezeigte Kopfzeile enthaelt nur PAGE, die nicht angezeigte zaehlt nicht
        assertEquals(2, metrics.get(Metric.DATA_FIELDS_CONTENT));
        assertEquals(0, metrics.get(Metric.DATA_FIELDS_HEADER_FOOTER));
        assertEquals(0, metrics.get(Metric.CONDITIONS_HEADER_FOOTER));
        assertTrue(metrics.fieldEntries().stream().allMatch(e -> e.region() == Region.CONTENT));
    }

    @Test
    void measuresSimpleTemplate() throws Exception {
        Path file = tempDir.resolve("einfach.docx");
        ComplexTemplates.writeSimple(file);

        TemplateMetrics metrics = new DocxMetricsExtractor().extract(file, "einfach.docx");

        assertEquals(1, metrics.get(Metric.PAGES));
        assertEquals(1, metrics.get(Metric.DATA_FIELDS));
        assertEquals(1, metrics.get(Metric.FIELD_NESTING));
        assertEquals(0, metrics.get(Metric.TABLES));
        assertEquals(0, metrics.get(Metric.CONDITIONS));
        assertEquals(3, metrics.get(Metric.PARAGRAPHS));
    }
}
