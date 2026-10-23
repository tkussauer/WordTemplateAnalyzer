package com.github.tkussauer.wta.complexity;

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

class ComplexityAppTest {

    @TempDir
    Path tempDir;

    @Test
    void writesComplexityWorkbook() throws Exception {
        Path input = Files.createDirectories(tempDir.resolve("vorlagen"));
        ComplexTemplates.writeSimple(input.resolve("einfach.docx"));
        ComplexTemplates.writeComplex(input.resolve("komplex.dotx"));
        Files.writeString(input.resolve("kaputt.doc"), "keine Word-Datei");

        Path output = tempDir.resolve("komplexitaet");
        assertEquals(ComplexityApp.EXIT_OK, ComplexityApp.run(new String[] {input.toString(), output.toString()}));

        try (InputStream in = Files.newInputStream(tempDir.resolve("komplexitaet.xlsx"));
             XSSFWorkbook workbook = new XSSFWorkbook(in)) {
            assertEquals(ComplexityReportWriter.SHEET_COMPLEXITY, workbook.getSheetName(0));
            List<List<String>> rows = rows(workbook.getSheet(ComplexityReportWriter.SHEET_COMPLEXITY));
            assertEquals(3, rows.size());
            assertEquals(List.of("Dokument", "Format", "Größe"), rows.get(0).subList(0, 3));
            // sortiert nach Groesse absteigend: die komplexe Vorlage zuerst
            assertEquals("komplex.dotx", rows.get(1).get(0));
            assertEquals("M", rows.get(1).get(2));
            assertEquals("einfach.docx", rows.get(2).get(0));
            assertEquals("S", rows.get(2).get(2));

            List<List<String>> summary = rows(workbook.getSheet(ComplexityReportWriter.SHEET_SUMMARY));
            assertEquals(List.of("S", "einfach", "0 bis < 20", "1"), summary.get(1).subList(0, 4));
            assertEquals(List.of("Gesamt", "", "", "2"), summary.get(5).subList(0, 4));

            assertNotNull(workbook.getSheet(ComplexityReportWriter.SHEET_POINTS));
            assertEquals(Metric.values().length + 1,
                    workbook.getSheet(ComplexityReportWriter.SHEET_MODEL).getPhysicalNumberOfRows());
            assertEquals(2, workbook.getSheet(ComplexityReportWriter.SHEET_ERRORS).getPhysicalNumberOfRows());
        }
    }

    @Test
    void usesCustomConfiguration() throws Exception {
        Path input = Files.createDirectories(tempDir.resolve("vorlagen"));
        ComplexTemplates.writeSimple(input.resolve("einfach.docx"));
        Path config = tempDir.resolve("schema.properties");
        Files.writeString(config, "schwelle.M=1\nschwelle.L=2\nschwelle.XL=3\n");

        Path output = tempDir.resolve("ergebnis.xlsx");
        assertEquals(ComplexityApp.EXIT_OK, ComplexityApp.run(
                new String[] {"-k", config.toString(), input.toString(), output.toString()}));
        try (InputStream in = Files.newInputStream(output); XSSFWorkbook workbook = new XSSFWorkbook(in)) {
            assertEquals("XL", rows(workbook.getSheet(ComplexityReportWriter.SHEET_COMPLEXITY)).get(1).get(2));
            assertNull(workbook.getSheet(ComplexityReportWriter.SHEET_ERRORS));
        }
    }

    @Test
    void rejectsInvalidArguments() throws Exception {
        assertEquals(ComplexityApp.EXIT_USAGE, ComplexityApp.run(new String[] {}));
        assertEquals(ComplexityApp.EXIT_USAGE, ComplexityApp.run(new String[] {"-k"}));
        Path input = Files.createDirectories(tempDir.resolve("vorlagen"));
        assertEquals(ComplexityApp.EXIT_USAGE, ComplexityApp.run(
                new String[] {"-k", tempDir.resolve("fehlt.properties").toString(), input.toString(), "x.xlsx"}));
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
