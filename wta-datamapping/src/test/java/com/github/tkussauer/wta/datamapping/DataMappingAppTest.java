package com.github.tkussauer.wta.datamapping;

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
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataMappingAppTest {

    @TempDir
    Path tempDir;

    @Test
    void writesConsolidatedVariables() throws Exception {
        Path input = Files.createDirectories(tempDir.resolve("vorlagen"));
        MappingTemplates.write(input.resolve("a.docx"));
        Path sub = Files.createDirectories(input.resolve("unterordner"));
        MappingTemplates.write(sub.resolve("b.dotx"));
        Files.writeString(input.resolve("kaputt.docx"), "keine Word-Datei");

        Path output = tempDir.resolve("mapping");
        assertEquals(DataMappingApp.EXIT_OK, DataMappingApp.run(new String[] {input.toString(), output.toString()}));

        try (InputStream in = Files.newInputStream(tempDir.resolve("mapping.xlsx"));
             XSSFWorkbook workbook = new XSSFWorkbook(in)) {
            assertEquals(DataMappingReportWriter.SHEET_VARIABLES, workbook.getSheetName(0));
            List<List<String>> rows = rows(workbook.getSheet(DataMappingReportWriter.SHEET_VARIABLES));
            List<String> header = rows.get(0);
            assertTrue(header.containsAll(DataMappingReportWriter.MAPPING_COLUMNS));
            int nameColumn = header.indexOf("Variable");
            Map<String, List<String>> byName = rows.subList(1, rows.size()).stream()
                    .collect(Collectors.toMap(r -> r.get(header.indexOf("Kategorie")) + ":" + r.get(nameColumn),
                            Function.identity()));

            // "Name" im Hauptdokument und "name" in der Kopfzeile sind dieselbe Variable
            List<String> name = byName.get("Seriendruckfeld:Name");
            assertEquals("4", name.get(header.indexOf("Vorkommen")));
            assertEquals("2", name.get(header.indexOf("Anzahl Vorlagen")));
            assertEquals("a.docx, unterordner" + java.io.File.separator + "b.dotx",
                    name.get(header.indexOf("Vorlagen")));

            assertEquals("Datum", byName.get("Seriendruckfeld:Rechnungsdatum").get(header.indexOf("Datentyp")));
            assertEquals("Ja", byName.get("Seriendruckfeld:Anrede").get(header.indexOf("Innerhalb IF-Feld")));
            assertEquals("Nein", byName.get("Dokumentvariable:Altlast").get(header.indexOf("Im Text verwendet")));
            assertEquals("Ja", byName.get("Dokumentvariable:Kundennummer").get(header.indexOf("Im Text verwendet")));
            assertTrue(byName.containsKey("Platzhalter:Strasse"));
            assertFalse(byName.containsKey("Platzhalter:Name"));

            // sortiert nach Kategorie: Seriendruckfelder zuerst
            assertEquals("Seriendruckfeld", rows.get(1).get(header.indexOf("Kategorie")));

            List<List<String>> templates = rows(workbook.getSheet(DataMappingReportWriter.SHEET_TEMPLATES));
            assertEquals(3, templates.size());
            assertEquals(2, rows(workbook.getSheet(DataMappingReportWriter.SHEET_ERRORS)).size());
        }
    }

    @Test
    void supportsCustomPlaceholdersAndSkippingBookmarks() throws Exception {
        Path input = Files.createDirectories(tempDir.resolve("vorlagen"));
        MappingTemplates.write(input.resolve("a.docx"));

        Path output = tempDir.resolve("mapping.xlsx");
        assertEquals(DataMappingApp.EXIT_OK, DataMappingApp.run(new String[] {
                "--ohne-textmarken", "-p", "wohnhaft (in)", input.toString(), output.toString()}));
        try (InputStream in = Files.newInputStream(output); XSSFWorkbook workbook = new XSSFWorkbook(in)) {
            List<List<String>> rows = rows(workbook.getSheet(DataMappingReportWriter.SHEET_VARIABLES));
            int kind = rows.get(0).indexOf("Kategorie");
            int name = rows.get(0).indexOf("Variable");
            assertTrue(rows.stream().noneMatch(r -> "Textmarke".equals(r.get(kind)) && "Betreff".equals(r.get(name))));
            assertTrue(rows.stream().anyMatch(r -> "Platzhalter".equals(r.get(kind)) && "in".equals(r.get(name))));
            assertNull(workbook.getSheet(DataMappingReportWriter.SHEET_ERRORS));
        }
    }

    @Test
    void rejectsInvalidArguments() throws Exception {
        assertEquals(DataMappingApp.EXIT_USAGE, DataMappingApp.run(new String[] {}));
        assertEquals(DataMappingApp.EXIT_USAGE, DataMappingApp.run(new String[] {"-p"}));
        Path input = Files.createDirectories(tempDir.resolve("vorlagen"));
        assertEquals(DataMappingApp.EXIT_USAGE,
                DataMappingApp.run(new String[] {"-p", "([", input.toString(), "x.xlsx"}));
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
