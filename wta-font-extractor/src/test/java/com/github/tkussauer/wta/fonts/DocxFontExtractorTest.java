package com.github.tkussauer.wta.fonts;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocxFontExtractorTest {

    @TempDir
    Path tempDir;

    @Test
    void resolvesEffectiveFontStyles() throws Exception {
        Path file = tempDir.resolve("vorlage.docx");
        TestDocuments.writeSample(file);

        DocumentFonts result = new DocxFontExtractor().extract(file, "vorlage.docx");

        Map<String, FontUsage> bySample = result.usages().stream()
                .collect(Collectors.toMap(FontUsage::sampleText, Function.identity()));
        assertEquals(5, bySample.size(), bySample.keySet().toString());

        FontUsage title = bySample.get("Anschreiben");
        assertEquals(new FontStyle("Arial", 16.0, true, false, false, "1F3864"), title.style());
        assertEquals("heading 1", title.paragraphStyle());
        assertEquals("Hauptdokument", title.area());
        assertFalse(title.directFormatting());

        FontUsage normal = bySample.get("Sehr geehrte Damen und Herren,");
        assertEquals(new FontStyle("Calibri", 11.0, false, false, false, FontStyle.AUTO_COLOR), normal.style());
        assertEquals("Normal", normal.paragraphStyle());
        assertNull(normal.characterStyle());

        FontUsage emphasized = bySample.get("wichtig");
        assertEquals(new FontStyle("Calibri", 11.0, false, true, false, FontStyle.AUTO_COLOR), emphasized.style());
        assertEquals("Betont", emphasized.characterStyle());

        FontUsage direct = bySample.get("Kleingedrucktes");
        assertEquals(new FontStyle("Courier New", 9.0, false, false, true, "FF0000"), direct.style());
        assertTrue(direct.directFormatting());

        FontUsage header = bySample.get("Firma GmbH");
        assertEquals("Kopfzeile", header.area());
        assertEquals("Calibri", header.style().fontName());
    }

    @Test
    void reportsStyleDefinitionsWithUsage() throws Exception {
        Path file = tempDir.resolve("vorlage.dotx");
        TestDocuments.writeSample(file);

        List<StyleDefinition> styles = new DocxFontExtractor().extract(file, "vorlage.dotx").styles();
        Map<String, StyleDefinition> byId = styles.stream()
                .collect(Collectors.toMap(StyleDefinition::styleId, Function.identity()));

        StyleDefinition heading = byId.get("Ueberschrift1");
        assertEquals("Absatz", heading.type());
        assertEquals("Normal", heading.basedOn());
        assertEquals(new FontStyle("Arial", 16.0, true, false, false, "1F3864"), heading.style());
        assertTrue(heading.used());

        StyleDefinition emphasis = byId.get("Betont");
        assertEquals("Zeichen", emphasis.type());
        assertTrue(emphasis.style().italic());
        assertTrue(emphasis.used());

        StyleDefinition footer = byId.get("Fusszeile");
        assertEquals(8.0, footer.style().sizePt());
        assertEquals("Calibri", footer.style().fontName());
        assertFalse(footer.used());
    }
}
