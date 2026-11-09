package com.github.tkussauer.wta.conditions;

import com.github.tkussauer.wta.common.ExcelSheetWriter;
import com.github.tkussauer.wta.common.ExtractionFailure;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFConditionalFormattingRule;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Schreibt die Bedingungen in eine Excel-Datei.
 * <ul>
 *     <li><b>Bedingungen</b> – jede Bedingung mit Zerlegung, Klartext und normalisiertem Ausdruck</li>
 *     <li><b>Felder</b> – welche Datenfelder in Bedingungen abgefragt werden, mit Operatoren und Vergleichswerten</li>
 *     <li><b>Übersicht</b> – Anzahl und Verschachtelungstiefe je Vorlage</li>
 *     <li><b>Fehler</b> – nicht lesbare Dokumente (nur falls vorhanden)</li>
 * </ul>
 */
public class ConditionReportWriter {

    public static final String SHEET_CONDITIONS = "Bedingungen";
    public static final String SHEET_FIELDS = "Felder";
    public static final String SHEET_SUMMARY = "Übersicht";
    public static final String SHEET_ERRORS = "Fehler";

    static final List<String> HEADERS = List.of("Vorlage", "Bereich", "Nr.", "Ebene", "Lage", "Art",
            "Bedingung (Klartext)", "Abgefragte Felder", "Operator", "Vergleichswert", "Dann", "Sonst",
            "Ausdruck (normalisiert)", "Felder im Dann/Sonst-Text", "Hinweise", "Aktuelles Ergebnis", "Kontext",
            "Feldcode");
    private static final int COL_ID = 2;
    private static final int COL_LEVEL = 3;
    private static final int COL_READABLE = 6;
    /** Spalten mit umbrochenem Text und fester Breite (in Zeichen). */
    private static final Map<Integer, Integer> WRAPPED = Map.of(6, 70, 10, 35, 11, 35, 12, 40, 14, 45, 16, 50, 17, 60);
    private static final int MAX_INDENT = 6;

    private static final class FieldSummary {
        int count;
        final Set<String> operators = new TreeSet<>();
        final Set<String> values = new LinkedHashSet<>();
        final Set<String> documents = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        boolean onlyHidden = true;
    }

    public void write(ConditionAnalysis.Result result, Path output) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            writeConditions(workbook, result.templates());
            writeFields(workbook, result.templates());
            writeSummary(workbook, result.templates());
            if (!result.failures().isEmpty()) {
                ExcelSheetWriter sheet = new ExcelSheetWriter(workbook, SHEET_ERRORS, List.of("Dokument", "Fehlermeldung"));
                for (ExtractionFailure failure : result.failures()) {
                    sheet.addRow(failure.document(), failure.message());
                }
                sheet.finish();
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

    private void writeConditions(XSSFWorkbook workbook, List<ConditionAnalysis.TemplateConditions> templates) {
        ExcelSheetWriter sheet = new ExcelSheetWriter(workbook, SHEET_CONDITIONS, HEADERS);
        List<Row> rows = new ArrayList<>();
        List<Condition> all = new ArrayList<>();
        for (ConditionAnalysis.TemplateConditions template : templates) {
            for (Condition c : template.conditions()) {
                // Arrays.asList statt List.of, da einige Werte leer sein koennen
                rows.add(sheet.addRow(Arrays.asList(c.document(), c.area(), c.id(), c.level(), c.position(),
                        c.kind(), c.readable(), String.join(", ", c.conditionFields()), c.operator(),
                        c.compareValue(), c.thenText(), c.elseText(), c.expression(),
                        String.join(", ", c.textFields()), String.join("\n", c.notes()), c.result(), c.context(),
                        c.fieldCode()).toArray()));
                all.add(c);
            }
        }
        sheet.finish();
        XSSFSheet xssf = workbook.getSheet(SHEET_CONDITIONS);

        // Klartext je Ebene eingerueckt, lange Texte umbrochen
        XSSFCellStyle top = workbook.createCellStyle();
        top.setVerticalAlignment(VerticalAlignment.TOP);
        XSSFCellStyle wrap = workbook.createCellStyle();
        wrap.cloneStyleFrom(top);
        wrap.setWrapText(true);
        XSSFCellStyle bold = workbook.createCellStyle();
        bold.cloneStyleFrom(top);
        XSSFFont boldFont = workbook.createFont();
        boldFont.setBold(true);
        bold.setFont(boldFont);
        bold.setAlignment(HorizontalAlignment.CENTER);
        XSSFCellStyle centered = workbook.createCellStyle();
        centered.cloneStyleFrom(top);
        centered.setAlignment(HorizontalAlignment.CENTER);
        List<XSSFCellStyle> indented = new ArrayList<>();
        for (int level = 0; level < MAX_INDENT; level++) {
            XSSFCellStyle style = workbook.createCellStyle();
            style.cloneStyleFrom(wrap);
            // Excel rueckt nur bei expliziter Linksausrichtung ein
            style.setAlignment(HorizontalAlignment.LEFT);
            style.setIndention((short) (level * 2));
            indented.add(style);
        }
        for (int r = 0; r < rows.size(); r++) {
            Row row = rows.get(r);
            Condition condition = all.get(r);
            int lines = 1;
            for (Cell cell : row) {
                int column = cell.getColumnIndex();
                if (column == COL_READABLE) {
                    cell.setCellStyle(indented.get(Math.min(condition.level() - 1, MAX_INDENT - 1)));
                } else if (column == COL_ID) {
                    cell.setCellStyle(bold);
                } else if (column == COL_LEVEL) {
                    cell.setCellStyle(centered);
                } else if (WRAPPED.containsKey(column)) {
                    cell.setCellStyle(wrap);
                } else {
                    cell.setCellStyle(top);
                }
                if (WRAPPED.containsKey(column) && cell.getCellType() == CellType.STRING) {
                    lines = Math.max(lines, lines(cell.getStringCellValue(), WRAPPED.get(column)));
                }
            }
            row.setHeightInPoints(Math.min(409, 15 * Math.min(lines, 12) + 2));
        }
        WRAPPED.forEach((column, width) -> xssf.setColumnWidth(column, width * 256));

        // Bedingungen aus nicht angezeigten Kopf-/Fusszeilen grau und kursiv
        if (!rows.isEmpty()) {
            XSSFConditionalFormattingRule hidden = xssf.getSheetConditionalFormatting()
                    .createConditionalFormattingRule("ISNUMBER(SEARCH(\"nicht angezeigt\",$B2))");
            hidden.createFontFormatting().setFontStyle(true, false);
            hidden.getFontFormatting().setFontColor(new XSSFColor(HexFormat.of().parseHex("808080")));
            xssf.getSheetConditionalFormatting().addConditionalFormatting(
                    new CellRangeAddress[] {new CellRangeAddress(1, rows.size(), 0, HEADERS.size() - 1)}, hidden);
        }
        xssf.createFreezePane(3, 1);
    }

    private void writeFields(XSSFWorkbook workbook, List<ConditionAnalysis.TemplateConditions> templates) {
        Map<String, FieldSummary> fields = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (ConditionAnalysis.TemplateConditions template : templates) {
            for (Condition c : template.conditions()) {
                for (String field : c.conditionFields()) {
                    FieldSummary summary = fields.computeIfAbsent(field, f -> new FieldSummary());
                    summary.count++;
                    summary.documents.add(c.document());
                    summary.onlyHidden &= c.hidden();
                    if (c.operator() != null) {
                        summary.operators.add(c.operator());
                    }
                    if (c.compareValue() != null) {
                        summary.values.add(c.compareValue().isEmpty() ? "(leer)" : c.compareValue());
                    }
                }
            }
        }
        ExcelSheetWriter sheet = new ExcelSheetWriter(workbook, SHEET_FIELDS, List.of("Feld", "Feldart",
                "Bedingungen", "Operatoren", "Vergleichswerte", "Nur in nicht angezeigten Bereichen", "Vorlagen"));
        fields.forEach((field, s) -> {
            int space = field.indexOf(' ');
            sheet.addRow(field.substring(space + 1), field.substring(0, space), s.count,
                    String.join(" ", s.operators), String.join(" | ", s.values), s.onlyHidden,
                    String.join(", ", s.documents));
        });
        sheet.finish();
    }

    private void writeSummary(XSSFWorkbook workbook, List<ConditionAnalysis.TemplateConditions> templates) {
        ExcelSheetWriter sheet = new ExcelSheetWriter(workbook, SHEET_SUMMARY, List.of("Vorlage", "Format",
                "Bedingungen", "davon verschachtelt", "Max. Tiefe", "Abgefragte Felder", "In nicht angezeigten Bereichen",
                "IF", "COMPARE", "SKIPIF/NEXTIF"));
        for (ConditionAnalysis.TemplateConditions t : templates) {
            Set<String> fields = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            int nested = 0;
            int depth = 0;
            int hidden = 0;
            int ifs = 0;
            int compares = 0;
            int records = 0;
            for (Condition c : t.conditions()) {
                fields.addAll(c.conditionFields());
                nested += c.level() > 1 ? 1 : 0;
                depth = Math.max(depth, c.level());
                hidden += c.hidden() ? 1 : 0;
                switch (c.kind()) {
                    case "IF" -> ifs++;
                    case "COMPARE" -> compares++;
                    default -> records++;
                }
            }
            sheet.addRow(t.document(), t.format(), t.conditions().size(), nested, depth, fields.size(), hidden,
                    ifs, compares, records);
        }
        sheet.finish();
    }

    private static int lines(String text, int width) {
        int lines = 0;
        for (String line : text.split("\n", -1)) {
            lines += Math.max(1, (line.length() + width - 3) / (width - 2));
        }
        return lines;
    }
}
