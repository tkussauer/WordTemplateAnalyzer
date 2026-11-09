package com.github.tkussauer.wta.conditions;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.wp.usermodel.HeaderFooterType;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.xmlbeans.XmlOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBody;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConditionsAppTest {

    private static final String BODY = """
            <w:body xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
              <w:p>
                <w:r><w:fldChar w:fldCharType="begin"/></w:r>
                <w:r><w:instrText xml:space="preserve"> IF </w:instrText></w:r>
                <w:r><w:fldChar w:fldCharType="begin"/></w:r>
                <w:r><w:instrText xml:space="preserve"> MERGEFIELD Anrede </w:instrText></w:r>
                <w:r><w:fldChar w:fldCharType="separate"/></w:r>
                <w:r><w:t>«Anrede»</w:t></w:r>
                <w:r><w:fldChar w:fldCharType="end"/></w:r>
                <w:r><w:instrText xml:space="preserve"> = "Herr" "Sehr geehrter Herr" "Sehr geehrte Frau" </w:instrText></w:r>
                <w:r><w:fldChar w:fldCharType="separate"/></w:r>
                <w:r><w:t>Sehr geehrter Herr</w:t></w:r>
                <w:r><w:fldChar w:fldCharType="end"/></w:r>
              </w:p>
              <w:p><w:fldSimple w:instr=" MERGEFIELD Name "><w:r><w:t>«Name»</w:t></w:r></w:fldSimple></w:p>
              <w:sectPr/>
            </w:body>
            """;

    @TempDir
    Path tempDir;

    @Test
    void extractsConditionsFromSingleFile() throws Exception {
        Path file = tempDir.resolve("brief.docx");
        writeTemplate(file);

        Path output = tempDir.resolve("bedingungen");
        assertEquals(ConditionsApp.EXIT_OK, ConditionsApp.run(new String[] {file.toString(), output.toString()}));

        try (InputStream in = Files.newInputStream(tempDir.resolve("bedingungen.xlsx"));
             XSSFWorkbook workbook = new XSSFWorkbook(in)) {
            assertEquals(ConditionReportWriter.SHEET_CONDITIONS, workbook.getSheetName(0));
            List<List<String>> rows = rows(workbook.getSheet(ConditionReportWriter.SHEET_CONDITIONS));
            assertEquals(ConditionReportWriter.HEADERS, rows.get(0));
            assertEquals(3, rows.size());
            int area = rows.get(0).indexOf("Bereich");
            int readable = rows.get(0).indexOf("Bedingung (Klartext)");

            List<String> body = rows.get(1);
            assertEquals("brief.docx", body.get(0));
            assertEquals("Hauptdokument", body.get(area));
            assertEquals("WENN «Anrede» ist gleich „Herr“ DANN „Sehr geehrter Herr“ SONST „Sehr geehrte Frau“",
                    body.get(readable));
            assertEquals("Sehr geehrter Herr", body.get(rows.get(0).indexOf("Aktuelles Ergebnis")));

            // IF in der Kopfzeile "Erste Seite" ohne "Erste Seite anders"
            assertEquals("Kopfzeile (nicht angezeigt)", rows.get(2).get(area));
            assertTrue(rows.get(2).get(rows.get(0).indexOf("Hinweise")).contains("kein Datenfeld"));

            List<List<String>> fields = rows(workbook.getSheet(ConditionReportWriter.SHEET_FIELDS));
            // Das IF in der Kopfzeile vergleicht nur Konstanten und fragt kein Feld ab
            assertEquals(2, fields.size());
            assertEquals(List.of("Anrede", "MERGEFIELD", "1", "=", "Herr", "Nein", "brief.docx"), fields.get(1));

            List<List<String>> summary = rows(workbook.getSheet(ConditionReportWriter.SHEET_SUMMARY));
            assertEquals(List.of("brief.docx", "docx", "2", "0", "1", "1", "1", "2", "0", "0"), summary.get(1));
            assertNull(workbook.getSheet(ConditionReportWriter.SHEET_ERRORS));
        }
    }

    @Test
    void processesDirectoriesAndReportsErrors() throws Exception {
        Path input = Files.createDirectories(tempDir.resolve("vorlagen"));
        writeTemplate(input.resolve("a.docx"));
        Files.writeString(input.resolve("kaputt.doc"), "keine Word-Datei");

        Path output = tempDir.resolve("ergebnis.xlsx");
        assertEquals(ConditionsApp.EXIT_OK, ConditionsApp.run(new String[] {input.toString(), output.toString()}));
        try (InputStream in = Files.newInputStream(output); XSSFWorkbook workbook = new XSSFWorkbook(in)) {
            assertEquals(2, rows(workbook.getSheet(ConditionReportWriter.SHEET_ERRORS)).size());
        }
    }

    @Test
    void rejectsInvalidArguments() throws Exception {
        assertEquals(ConditionsApp.EXIT_USAGE, ConditionsApp.run(new String[] {}));
        assertEquals(ConditionsApp.EXIT_USAGE,
                ConditionsApp.run(new String[] {tempDir.resolve("fehlt.docx").toString(), "x.xlsx"}));
        Path text = Files.writeString(tempDir.resolve("notiz.txt"), "x");
        assertEquals(ConditionsApp.EXIT_ERROR, ConditionsApp.run(new String[] {text.toString(), "x.xlsx"}));
    }

    private static void writeTemplate(Path file) throws Exception {
        try (XWPFDocument doc = new XWPFDocument()) {
            // Wurzelelement w:body beim Laden entfernen, sonst entsteht w:body in w:body
            doc.getDocument().getBody().set(CTBody.Factory.parse(BODY,
                    new XmlOptions().setLoadReplaceDocumentElement(null)));
            doc.createHeader(HeaderFooterType.DEFAULT).createParagraph().createRun().setText("Kopf");
            doc.createHeader(HeaderFooterType.FIRST).createParagraph().getCTP().addNewFldSimple()
                    .setInstr(" IF 1 = 1 \"a\" \"b\" ");
            CTSectPr sectPr = doc.getDocument().getBody().getSectPr();
            while (sectPr.isSetTitlePg()) {
                sectPr.unsetTitlePg();
            }
            try (OutputStream out = Files.newOutputStream(file)) {
                doc.write(out);
            }
        }
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
