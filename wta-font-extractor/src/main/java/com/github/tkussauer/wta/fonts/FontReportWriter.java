package com.github.tkussauer.wta.fonts;

import com.github.tkussauer.wta.common.ExcelSheetWriter;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Schreibt das Ergebnis der Schriftanalyse in eine Excel-Datei.
 * <ul>
 *     <li><b>Schriftarten</b> – alle unterschiedlichen Schriftstile ueber alle Vorlagen</li>
 *     <li><b>Schriftfamilien</b> – benoetigte Schriftarten und Schnitte</li>
 *     <li><b>Details</b> – Verwendung je Dokument, Bereich und Formatvorlage</li>
 *     <li><b>Formatvorlagen</b> – definierte Absatz- und Zeichenformatvorlagen je Dokument</li>
 *     <li><b>Fehler</b> – nicht lesbare Dokumente (nur falls vorhanden)</li>
 * </ul>
 */
public class FontReportWriter {

    public static final String SHEET_FONTS = "Schriftarten";
    public static final String SHEET_FAMILIES = "Schriftfamilien";
    public static final String SHEET_DETAILS = "Details";
    public static final String SHEET_STYLES = "Formatvorlagen";
    public static final String SHEET_ERRORS = "Fehler";

    private static final class StyleSummary {
        int runs;
        int chars;
        final Set<String> documents = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        final Set<String> styles = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    }

    private static final class FamilySummary {
        int runs;
        final Set<String> weights = new TreeSet<>();
        final Set<String> documents = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    }

    public void write(FontAnalysis.Result result, Path output) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            writeFonts(workbook, result.documents());
            writeFamilies(workbook, result.documents());
            writeDetails(workbook, result.documents());
            writeStyles(workbook, result.documents());
            if (!result.failures().isEmpty()) {
                writeFailures(workbook, result.failures());
            }
            Path parent = output.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (OutputStream out = Files.newOutputStream(output)) {
                workbook.write(out);
            }
        }
    }

    private void writeFonts(XSSFWorkbook workbook, List<DocumentFonts> documents) {
        Map<FontStyle, StyleSummary> summaries = new TreeMap<>(FontStyle.ORDER);
        for (DocumentFonts document : documents) {
            for (FontUsage usage : document.usages()) {
                StyleSummary summary = summaries.computeIfAbsent(usage.style(), s -> new StyleSummary());
                summary.runs += usage.runCount();
                summary.chars += usage.characterCount();
                summary.documents.add(document.document());
                summary.styles.add(usage.characterStyle() != null ? usage.characterStyle() : usage.paragraphStyle());
            }
        }
        ExcelSheetWriter sheet = new ExcelSheetWriter(workbook, SHEET_FONTS, List.of(
                "Nr.", "Schriftart", "Schnitt", "Größe (pt)", "Fett", "Kursiv", "Unterstrichen", "Farbe (RGB)",
                "Textabschnitte", "Zeichen", "Anzahl Dokumente", "Formatvorlagen", "Dokumente"));
        int nr = 0;
        for (Map.Entry<FontStyle, StyleSummary> entry : summaries.entrySet()) {
            FontStyle style = entry.getKey();
            StyleSummary summary = entry.getValue();
            sheet.addRow(++nr, style.fontName(), style.weight(), style.sizePt(), style.bold(), style.italic(),
                    style.underline(), style.color(), summary.runs, summary.chars, summary.documents.size(),
                    String.join(", ", summary.styles), String.join(", ", summary.documents));
        }
        sheet.finish();
    }

    private void writeFamilies(XSSFWorkbook workbook, List<DocumentFonts> documents) {
        Map<String, FamilySummary> families = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (DocumentFonts document : documents) {
            for (FontUsage usage : document.usages()) {
                FamilySummary family = families.computeIfAbsent(usage.style().fontName(), f -> new FamilySummary());
                family.runs += usage.runCount();
                family.weights.add(usage.style().weight());
                family.documents.add(document.document());
            }
        }
        ExcelSheetWriter sheet = new ExcelSheetWriter(workbook, SHEET_FAMILIES, List.of(
                "Schriftart", "Benötigte Schnitte", "Textabschnitte", "Anzahl Dokumente", "Dokumente"));
        families.forEach((name, family) -> sheet.addRow(name, String.join(", ", family.weights), family.runs,
                family.documents.size(), String.join(", ", family.documents)));
        sheet.finish();
    }

    private void writeDetails(XSSFWorkbook workbook, List<DocumentFonts> documents) {
        ExcelSheetWriter sheet = new ExcelSheetWriter(workbook, SHEET_DETAILS, List.of(
                "Dokument", "Bereich", "Absatzformat", "Zeichenformat", "Direkte Formatierung",
                "Schriftart", "Schnitt", "Größe (pt)", "Fett", "Kursiv", "Unterstrichen", "Farbe (RGB)",
                "Textabschnitte", "Zeichen", "Textbeispiel"));
        for (DocumentFonts document : documents) {
            for (FontUsage usage : document.usages()) {
                FontStyle style = usage.style();
                sheet.addRow(document.document(), usage.area(), usage.paragraphStyle(), usage.characterStyle(),
                        usage.directFormatting(), style.fontName(), style.weight(), style.sizePt(), style.bold(),
                        style.italic(), style.underline(), style.color(), usage.runCount(), usage.characterCount(),
                        usage.sampleText());
            }
        }
        sheet.finish();
    }

    private void writeStyles(XSSFWorkbook workbook, List<DocumentFonts> documents) {
        ExcelSheetWriter sheet = new ExcelSheetWriter(workbook, SHEET_STYLES, List.of(
                "Dokument", "Formatvorlage", "ID", "Typ", "Basiert auf", "Verwendet",
                "Schriftart", "Schnitt", "Größe (pt)", "Fett", "Kursiv", "Unterstrichen", "Farbe (RGB)"));
        for (DocumentFonts document : documents) {
            List<StyleDefinition> styles = new ArrayList<>(document.styles());
            for (StyleDefinition definition : styles) {
                FontStyle style = definition.style();
                sheet.addRow(document.document(), definition.name(), definition.styleId(), definition.type(),
                        definition.basedOn(), definition.used(), style.fontName(), style.weight(), style.sizePt(),
                        style.bold(), style.italic(), style.underline(), style.color());
            }
        }
        sheet.finish();
    }

    private void writeFailures(XSSFWorkbook workbook, List<ExtractionFailure> failures) {
        ExcelSheetWriter sheet = new ExcelSheetWriter(workbook, SHEET_ERRORS, List.of("Dokument", "Fehlermeldung"));
        failures.forEach(f -> sheet.addRow(f.document(), f.message()));
        sheet.finish();
    }
}
