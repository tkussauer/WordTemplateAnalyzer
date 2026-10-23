package com.github.tkussauer.wta.fonts;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FontExtractorAppTest {

    @TempDir
    Path tempDir;

    @Test
    void writesFontsWorkbookForDirectory() throws Exception {
        Path input = Files.createDirectories(tempDir.resolve("vorlagen"));
        TestDocuments.writeSample(input.resolve("a.docx"));
        Path sub = Files.createDirectories(input.resolve("unterordner"));
        TestDocuments.writeSample(sub.resolve("b.dotx"));
        Files.writeString(input.resolve("kaputt.docx"), "keine Word-Datei");
        Files.writeString(input.resolve("~$a.docx"), "Sperrdatei");
        Files.writeString(input.resolve("notizen.txt"), "ignoriert");

        Path output = tempDir.resolve("ergebnis");
        int exit = FontExtractorApp.run(new String[] {input.toString(), output.toString()});
        assertEquals(FontExtractorApp.EXIT_OK, exit);

        Path xlsx = tempDir.resolve("ergebnis.xlsx");
        try (InputStream in = Files.newInputStream(xlsx); XSSFWorkbook workbook = new XSSFWorkbook(in)) {
            assertEquals(FontReportWriter.SHEET_FONTS, workbook.getSheetName(0));
            List<List<String>> fonts = rows(workbook.getSheet(FontReportWriter.SHEET_FONTS));
            assertEquals(List.of("Nr.", "Schriftart", "Schnitt", "Größe (pt)", "Fett", "Kursiv", "Unterstrichen",
                    "Farbe (RGB)", "Textabschnitte", "Zeichen", "Anzahl Dokumente", "Formatvorlagen", "Dokumente"),
                    fonts.get(0));
            // Arial 16 fett, Calibri 11, Calibri 11 kursiv, Courier New 9 unterstrichen
            assertEquals(5, fonts.size());
            List<String> arial = fonts.get(1);
            assertEquals("Arial", arial.get(1));
            assertEquals("Bold", arial.get(2));
            assertEquals("16.0", arial.get(3));
            assertEquals("1F3864", arial.get(7));
            assertEquals("2", arial.get(10));
            assertTrue(arial.get(12).contains("a.docx"));
            assertTrue(arial.get(12).contains("b.dotx"));

            assertNotNull(workbook.getSheet(FontReportWriter.SHEET_FAMILIES));
            assertNotNull(workbook.getSheet(FontReportWriter.SHEET_DETAILS));
            assertNotNull(workbook.getSheet(FontReportWriter.SHEET_STYLES));
            List<List<String>> errors = rows(workbook.getSheet(FontReportWriter.SHEET_ERRORS));
            assertEquals(2, errors.size());
            assertEquals("kaputt.docx", errors.get(1).get(0));
        }
    }

    @Test
    void omitsErrorSheetAndHonoursNonRecursiveOption() throws Exception {
        Path input = Files.createDirectories(tempDir.resolve("vorlagen"));
        TestDocuments.writeSample(input.resolve("a.docx"));
        Path sub = Files.createDirectories(input.resolve("unterordner"));
        Files.writeString(sub.resolve("kaputt.doc"), "keine Word-Datei");

        Path output = tempDir.resolve("ergebnis.xlsx");
        int exit = FontExtractorApp.run(new String[] {"-n", input.toString(), output.toString()});
        assertEquals(FontExtractorApp.EXIT_OK, exit);
        try (InputStream in = Files.newInputStream(output); XSSFWorkbook workbook = new XSSFWorkbook(in)) {
            assertNull(workbook.getSheet(FontReportWriter.SHEET_ERRORS));
        }
    }

    @Test
    void rejectsInvalidArguments() {
        assertEquals(FontExtractorApp.EXIT_USAGE, FontExtractorApp.run(new String[] {}));
        assertEquals(FontExtractorApp.EXIT_USAGE,
                FontExtractorApp.run(new String[] {tempDir.resolve("fehlt").toString(), "x.xlsx"}));
    }

    private static List<List<String>> rows(Sheet sheet) {
        // Feste Locale, damit Zahlen unabhaengig von den Systemeinstellungen formatiert werden
        DataFormatter formatter = new DataFormatter(Locale.ROOT);
        List<List<String>> rows = new ArrayList<>();
        for (Row row : sheet) {
            List<String> cells = new ArrayList<>();
            for (int i = 0; i < row.getLastCellNum(); i++) {
                cells.add(row.getCell(i) == null ? "" : formatter.formatCellValue(row.getCell(i)));
            }
            rows.add(cells);
        }
        return rows;
    }
}
