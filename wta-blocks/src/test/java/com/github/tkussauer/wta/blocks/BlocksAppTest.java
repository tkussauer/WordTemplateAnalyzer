package com.github.tkussauer.wta.blocks;

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

class BlocksAppTest {

    @TempDir
    Path tempDir;

    @Test
    void writesWorkbook() throws Exception {
        Path input = Files.createDirectories(tempDir.resolve("vorlagen"));
        BlockTemplates.writeAll(input);
        Files.writeString(input.resolve("kaputt.docx"), "keine Word-Datei");

        Path output = tempDir.resolve("bloecke");
        assertEquals(BlocksApp.EXIT_OK, BlocksApp.run(new String[] {input.toString(), output.toString()}));

        try (InputStream in = Files.newInputStream(tempDir.resolve("bloecke.xlsx"));
             XSSFWorkbook workbook = new XSSFWorkbook(in)) {
            assertEquals(List.of("Blöcke", "Block-Vorkommen", "Ähnliche Dokumente", "Ähnlichkeitsgruppen", "Matrix",
                    "Übersicht", "Parameter", "Fehler"), sheetNames(workbook));
            List<List<String>> blocks = rows(workbook.getSheet(BlocksReportWriter.SHEET_BLOCKS));
            assertEquals(4, blocks.size());
            assertEquals("B001", blocks.get(1).get(0));

            List<List<String>> pairs = rows(workbook.getSheet(BlocksReportWriter.SHEET_PAIRS));
            assertEquals(2, pairs.size());
            assertEquals(List.of("a.docx", "b.docx"), pairs.get(1).subList(0, 2));
            assertTrue(Double.parseDouble(pairs.get(1).get(2)) >= 80);
            assertEquals("G1", pairs.get(1).get(8));

            List<List<String>> matrix = rows(workbook.getSheet(BlocksReportWriter.SHEET_MATRIX));
            assertEquals(4, matrix.size());
            assertEquals("", matrix.get(1).get(1));
            assertEquals(matrix.get(1).get(2), matrix.get(2).get(1));
        }
    }

    @Test
    void appliesThresholdOption() throws Exception {
        Path input = Files.createDirectories(tempDir.resolve("vorlagen"));
        BlockTemplates.writeAll(input);
        Path output = tempDir.resolve("ergebnis.xlsx");
        assertEquals(BlocksApp.EXIT_OK,
                BlocksApp.run(new String[] {"-s", "100", input.toString(), output.toString()}));
        try (InputStream in = Files.newInputStream(output); XSSFWorkbook workbook = new XSSFWorkbook(in)) {
            assertEquals(1, rows(workbook.getSheet(BlocksReportWriter.SHEET_PAIRS)).size());
            assertNotNull(workbook.getSheet(BlocksReportWriter.SHEET_SETTINGS));
            assertEquals("100", rows(workbook.getSheet(BlocksReportWriter.SHEET_SETTINGS)).get(1).get(1));
            assertNull(workbook.getSheet(BlocksReportWriter.SHEET_ERRORS));
        }
    }

    @Test
    void rejectsInvalidArguments() {
        assertEquals(BlocksApp.EXIT_USAGE, BlocksApp.run(new String[] {}));
        assertEquals(BlocksApp.EXIT_USAGE, BlocksApp.run(new String[] {"-s", "120", "a", "b"}));
        assertEquals(BlocksApp.EXIT_USAGE, BlocksApp.run(new String[] {"-s", "x", "a", "b"}));
        assertEquals(BlocksApp.EXIT_USAGE, BlocksApp.run(new String[] {"--min-vorlagen", "1", "a", "b"}));
    }

    private static List<String> sheetNames(XSSFWorkbook workbook) {
        List<String> names = new ArrayList<>();
        workbook.forEach(sheet -> names.add(sheet.getSheetName()));
        return names;
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
