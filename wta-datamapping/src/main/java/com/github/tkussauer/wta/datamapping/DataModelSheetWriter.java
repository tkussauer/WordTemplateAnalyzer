package com.github.tkussauer.wta.datamapping;

import org.apache.poi.ss.SpreadsheetVersion;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.ComparisonOperator;
import org.apache.poi.ss.usermodel.ConditionalFormattingThreshold;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.PrintSetup;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.AreaReference;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFConditionalFormattingRule;
import org.apache.poi.xssf.usermodel.XSSFDataBarFormatting;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFSheetConditionalFormatting;
import org.apache.poi.xssf.usermodel.XSSFTable;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.CTTableStyleInfo;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Schreibt das Arbeitsblatt "DataModel": den Vorschlag fuer das Inspire-Datenmodell als formatierte
 * Excel-Tabelle mit Kennzahlen, farbigen Datentypen, Abdeckungsbalken, Gruppentrennern,
 * Auswahl "Übernehmen" und Legende.
 */
final class DataModelSheetWriter {

    static final String SHEET_NAME = "DataModel";
    static final String TABLE_NAME = "Datenmodell";

    static final List<String> HEADERS = List.of("Nr.", "Gruppe", "Feldname", "Pfad", "Inspire-Typ", "Format (Word)",
            "Werteliste", "Beispielwert", "Verwendung", "Vorlagen", "Abdeckung", "Herkunft (Word)", "Hinweise",
            "Übernehmen", "Bemerkung");
    private static final int[] WIDTHS = {6, 16, 26, 36, 13, 16, 28, 18, 12, 10, 13, 44, 52, 13, 30};

    private static final int COL_GROUP = 1;
    private static final int COL_TYPE = 4;
    private static final int COL_USAGE = 8;
    private static final int COL_COVERAGE = 10;
    private static final int COL_NOTES = 12;
    private static final int COL_ACCEPT = 13;
    private static final int LAST_COL = HEADERS.size() - 1;
    private static final float LINE_HEIGHT = 13.5f;

    private static final int ROW_TITLE = 0;
    private static final int ROW_SUBTITLE = 1;
    private static final int ROW_KPI_LABEL = 3;
    private static final int ROW_KPI_VALUE = 4;
    private static final int ROW_HEADER = 6;

    private static final String DARK = "1F3864";
    private static final Map<InspireType, String> TYPE_COLORS = new EnumMap<>(Map.of(
            InspireType.STRING, "D9E1F2",
            InspireType.INT, "FFF2CC",
            InspireType.DOUBLE, "FCE4D6",
            InspireType.CURRENCY, "FFE699",
            InspireType.DATE_TIME, "E2EFDA",
            InspireType.BOOL, "EADCF8",
            InspireType.ARRAY, "F8CBAD"));

    private final XSSFWorkbook workbook;
    private final XSSFSheet sheet;

    DataModelSheetWriter(XSSFWorkbook workbook) {
        this.workbook = workbook;
        this.sheet = workbook.createSheet(SHEET_NAME);
    }

    void write(List<ModelField> fields, int templateCount) {
        sheet.setTabColor(color(DARK));
        sheet.setDisplayGridlines(false);
        for (int i = 0; i < WIDTHS.length; i++) {
            sheet.setColumnWidth(i, WIDTHS[i] * 256);
        }
        writeTitle(fields, templateCount);
        writeKpis(fields, templateCount);
        int lastRow = writeTable(fields, templateCount);
        writeLegend(lastRow + 3);

        sheet.createFreezePane(3, ROW_HEADER + 1);
        PrintSetup print = sheet.getPrintSetup();
        print.setLandscape(true);
        print.setPaperSize(PrintSetup.A4_PAPERSIZE);
        print.setFitWidth((short) 1);
        print.setFitHeight((short) 0);
        sheet.setFitToPage(true);
        sheet.setRepeatingRows(new CellRangeAddress(ROW_HEADER, ROW_HEADER, -1, -1));
        sheet.getHeader().setCenter("Datenmodell-Vorschlag für Quadient Inspire");
        sheet.getFooter().setRight("Seite &P von &N");
    }

    private void writeTitle(List<ModelField> fields, int templateCount) {
        XSSFCellStyle title = style(DARK);
        title.setFont(font(true, false, 18, "FFFFFF"));
        title.setVerticalAlignment(VerticalAlignment.CENTER);
        title.setIndention((short) 1);
        XSSFRow row = sheet.createRow(ROW_TITLE);
        row.setHeightInPoints(36);
        for (int i = 0; i <= LAST_COL; i++) {
            row.createCell(i).setCellStyle(title);
        }
        row.getCell(0).setCellValue("Datenmodell-Vorschlag für Quadient Inspire");
        sheet.addMergedRegion(new CellRangeAddress(ROW_TITLE, ROW_TITLE, 0, LAST_COL));

        XSSFCellStyle subtitle = workbook.createCellStyle();
        subtitle.setFont(font(false, true, 10, "595959"));
        subtitle.setIndention((short) 1);
        XSSFRow sub = sheet.createRow(ROW_SUBTITLE);
        sub.setHeightInPoints(20);
        XSSFCell cell = sub.createCell(0);
        cell.setCellStyle(subtitle);
        cell.setCellValue(String.format("Konsolidiert und ohne Dubletten aus %d Vorlage(n), erstellt am %s. "
                        + "Feldnamen sind gültige Inspire-Bezeichner; Gruppen, Datentypen und Hinweise bitte fachlich "
                        + "prüfen und in der Spalte „Übernehmen“ entscheiden.",
                templateCount, LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))));
        sheet.addMergedRegion(new CellRangeAddress(ROW_SUBTITLE, ROW_SUBTITLE, 0, LAST_COL));
    }

    /** Fuenf Kacheln mit je drei Spalten Breite. */
    private void writeKpis(List<ModelField> fields, int templateCount) {
        Set<String> groups = new TreeSet<>();
        int merged = 0;
        int withNotes = 0;
        for (ModelField field : fields) {
            groups.add(field.group());
            merged += field.mergedCount() - 1;
            withNotes += field.notes().isEmpty() ? 0 : 1;
        }
        String[] labels = {"FELDER IM MODELL", "GRUPPEN", "AUSGEWERTETE VORLAGEN", "ZUSAMMENGEFÜHRTE DUBLETTEN",
                "FELDER MIT HINWEISEN"};
        int[] values = {fields.size(), groups.size(), templateCount, merged, withNotes};

        XSSFCellStyle label = style("DEEAF6");
        label.setFont(font(true, false, 8, "44546A"));
        label.setIndention((short) 1);
        label.setVerticalAlignment(VerticalAlignment.BOTTOM);
        XSSFCellStyle value = style("DEEAF6");
        value.setFont(font(true, false, 20, DARK));
        value.setAlignment(HorizontalAlignment.LEFT);
        value.setIndention((short) 1);
        value.setVerticalAlignment(VerticalAlignment.CENTER);
        XSSFCellStyle warning = style("FFF2CC");
        warning.cloneStyleFrom(value);
        warning.setFillForegroundColor(color("FFF2CC"));
        XSSFCellStyle warningLabel = style("FFF2CC");
        warningLabel.cloneStyleFrom(label);
        warningLabel.setFillForegroundColor(color("FFF2CC"));
        for (XSSFCellStyle s : List.of(label, value, warning, warningLabel)) {
            // weisse Raender trennen die Kacheln optisch
            s.setBorderLeft(BorderStyle.THICK);
            s.setBorderRight(BorderStyle.THICK);
            s.setLeftBorderColor(color("FFFFFF"));
            s.setRightBorderColor(color("FFFFFF"));
        }

        XSSFRow labelRow = sheet.createRow(ROW_KPI_LABEL);
        labelRow.setHeightInPoints(18);
        XSSFRow valueRow = sheet.createRow(ROW_KPI_VALUE);
        valueRow.setHeightInPoints(34);
        for (int tile = 0; tile < labels.length; tile++) {
            boolean highlight = tile == labels.length - 1 && values[tile] > 0;
            int first = tile * 3;
            for (int c = first; c < first + 3; c++) {
                labelRow.createCell(c).setCellStyle(highlight ? warningLabel : label);
                valueRow.createCell(c).setCellStyle(highlight ? warning : value);
            }
            labelRow.getCell(first).setCellValue(labels[tile]);
            valueRow.getCell(first).setCellValue(values[tile]);
            sheet.addMergedRegion(new CellRangeAddress(ROW_KPI_LABEL, ROW_KPI_LABEL, first, first + 2));
            sheet.addMergedRegion(new CellRangeAddress(ROW_KPI_VALUE, ROW_KPI_VALUE, first, first + 2));
        }
    }

    /** @return letzte Zeile der Tabelle */
    private int writeTable(List<ModelField> fields, int templateCount) {
        XSSFRow header = sheet.createRow(ROW_HEADER);
        header.setHeightInPoints(22);
        // Explizite Fuellung, damit die Kopfzeile auch ohne Tabellenformat (z. B. LibreOffice) lesbar ist
        XSSFCellStyle headerStyle = style("2F5597");
        headerStyle.setFont(font(true, false, 10, "FFFFFF"));
        headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        for (int i = 0; i < HEADERS.size(); i++) {
            XSSFCell cell = header.createCell(i);
            cell.setCellValue(HEADERS.get(i));
            cell.setCellStyle(headerStyle);
        }
        if (fields.isEmpty()) {
            XSSFCellStyle hint = workbook.createCellStyle();
            hint.setFont(font(false, true, 10, "595959"));
            XSSFCell cell = sheet.createRow(ROW_HEADER + 1).createCell(0);
            cell.setCellValue("In den Vorlagen wurden keine Variablen gefunden.");
            cell.setCellStyle(hint);
            return ROW_HEADER + 1;
        }

        XSSFCellStyle top = workbook.createCellStyle();
        top.setVerticalAlignment(VerticalAlignment.TOP);
        XSSFCellStyle wrap = workbook.createCellStyle();
        wrap.cloneStyleFrom(top);
        wrap.setWrapText(true);
        XSSFCellStyle bold = workbook.createCellStyle();
        bold.cloneStyleFrom(top);
        bold.setFont(font(true, false, 10, "000000"));
        XSSFCellStyle path = workbook.createCellStyle();
        path.cloneStyleFrom(top);
        path.setFont(monospace());
        XSSFCellStyle centered = workbook.createCellStyle();
        centered.cloneStyleFrom(top);
        centered.setAlignment(HorizontalAlignment.CENTER);
        XSSFCellStyle typeStyle = workbook.createCellStyle();
        typeStyle.cloneStyleFrom(centered);
        typeStyle.setFont(font(true, false, 10, "000000"));
        XSSFCellStyle percent = workbook.createCellStyle();
        percent.cloneStyleFrom(top);
        percent.setDataFormat(workbook.createDataFormat().getFormat("0%"));

        int rowIndex = ROW_HEADER;
        int nr = 0;
        for (ModelField field : fields) {
            XSSFRow row = sheet.createRow(++rowIndex);
            set(row, 0, ++nr, centered);
            set(row, 1, field.group(), top);
            set(row, 2, field.name(), bold);
            set(row, 3, field.path(), path);
            set(row, 4, field.type().inspireName(), typeStyle);
            set(row, 5, String.join("; ", field.formats()), top);
            set(row, 6, String.join(" | ", field.values()), wrap);
            set(row, 7, field.example(), top);
            set(row, 8, field.conditional() ? "bedingt" : "immer", centered);
            set(row, 9, field.templates().size(), centered);
            set(row, 10, templateCount == 0 ? 0.0 : (double) field.templates().size() / templateCount, percent);
            set(row, 11, String.join("\n", field.sources()), wrap);
            set(row, 12, String.join("\n", field.notes()), wrap);
            set(row, 13, "Ja", centered);
            set(row, 14, null, wrap);
            row.setHeightInPoints(LINE_HEIGHT * lines(field) + 3);
        }
        int lastRow = rowIndex;

        AreaReference area = new AreaReference(new CellReference(ROW_HEADER, 0), new CellReference(lastRow, LAST_COL),
                SpreadsheetVersion.EXCEL2007);
        XSSFTable table = sheet.createTable(area);
        table.setName(TABLE_NAME);
        table.setDisplayName(TABLE_NAME);
        CTTableStyleInfo tableStyle = table.getCTTable().addNewTableStyleInfo();
        tableStyle.setName("TableStyleMedium2");
        tableStyle.setShowRowStripes(true);
        tableStyle.setShowColumnStripes(false);
        table.getCTTable().addNewAutoFilter().setRef(area.formatAsString());

        addConditionalFormats(ROW_HEADER + 1, lastRow);
        addAcceptValidation(ROW_HEADER + 1, lastRow);
        return lastRow;
    }

    private void addConditionalFormats(int firstRow, int lastRow) {
        XSSFSheetConditionalFormatting formatting = sheet.getSheetConditionalFormatting();
        String first = String.valueOf(firstRow + 1);

        // Farbige Datentypen
        CellRangeAddress[] types = {new CellRangeAddress(firstRow, lastRow, COL_TYPE, COL_TYPE)};
        for (InspireType type : InspireType.values()) {
            XSSFConditionalFormattingRule rule = formatting.createConditionalFormattingRule(
                    ComparisonOperator.EQUAL, "\"" + type.inspireName() + "\"");
            rule.createPatternFormatting().setFillBackgroundColor(color(TYPE_COLORS.get(type)));
            formatting.addConditionalFormatting(types, rule);
        }

        // Abdeckung als Datenbalken von 0 % bis 100 %
        XSSFConditionalFormattingRule bar = formatting.createConditionalFormattingRule(color("5B9BD5"));
        XSSFDataBarFormatting dataBar = bar.getDataBarFormatting();
        dataBar.getMinThreshold().setRangeType(ConditionalFormattingThreshold.RangeType.NUMBER);
        dataBar.getMinThreshold().setValue(0.0);
        dataBar.getMaxThreshold().setRangeType(ConditionalFormattingThreshold.RangeType.NUMBER);
        dataBar.getMaxThreshold().setValue(1.0);
        formatting.addConditionalFormatting(
                new CellRangeAddress[] {new CellRangeAddress(firstRow, lastRow, COL_COVERAGE, COL_COVERAGE)}, bar);

        // Hinweise hervorheben
        String notes = CellReference.convertNumToColString(COL_NOTES);
        XSSFConditionalFormattingRule hint = formatting.createConditionalFormattingRule(
                "LEN($" + notes + first + ")>0");
        hint.createPatternFormatting().setFillBackgroundColor(color("FFF2CC"));
        formatting.addConditionalFormatting(
                new CellRangeAddress[] {new CellRangeAddress(firstRow, lastRow, COL_NOTES, COL_NOTES)}, hint);

        // Uebernehmen: Ja gruen, Nein rot
        CellRangeAddress[] accept = {new CellRangeAddress(firstRow, lastRow, COL_ACCEPT, COL_ACCEPT)};
        XSSFConditionalFormattingRule yes = formatting.createConditionalFormattingRule(
                ComparisonOperator.EQUAL, "\"Ja\"");
        yes.createPatternFormatting().setFillBackgroundColor(color("C6EFCE"));
        yes.createFontFormatting().setFontColor(color("006100"));
        XSSFConditionalFormattingRule no = formatting.createConditionalFormattingRule(
                ComparisonOperator.EQUAL, "\"Nein\"");
        no.createPatternFormatting().setFillBackgroundColor(color("FFC7CE"));
        no.createFontFormatting().setFontColor(color("9C0006"));
        formatting.addConditionalFormatting(accept, yes, no);

        // Bedingt verwendete Felder kursiv
        String usage = CellReference.convertNumToColString(COL_USAGE);
        XSSFConditionalFormattingRule conditional = formatting.createConditionalFormattingRule(
                "$" + usage + first + "=\"bedingt\"");
        conditional.createFontFormatting().setFontStyle(true, false);
        conditional.getFontFormatting().setFontColor(color("7F6000"));
        formatting.addConditionalFormatting(
                new CellRangeAddress[] {new CellRangeAddress(firstRow, lastRow, COL_USAGE, COL_USAGE)}, conditional);

        // Trennlinie bei jedem Gruppenwechsel
        String group = CellReference.convertNumToColString(COL_GROUP);
        XSSFConditionalFormattingRule separator = formatting.createConditionalFormattingRule(
                "$" + group + first + "<>$" + group + firstRow);
        separator.createBorderFormatting().setBorderTop(BorderStyle.MEDIUM);
        separator.getBorderFormatting().setTopBorderColor(color(DARK));
        formatting.addConditionalFormatting(
                new CellRangeAddress[] {new CellRangeAddress(firstRow, lastRow, 0, LAST_COL)}, separator);
    }

    private void addAcceptValidation(int firstRow, int lastRow) {
        DataValidationHelper helper = sheet.getDataValidationHelper();
        DataValidation validation = helper.createValidation(
                helper.createExplicitListConstraint(new String[] {"Ja", "Nein"}),
                new CellRangeAddressList(firstRow, lastRow, COL_ACCEPT, COL_ACCEPT));
        validation.setShowErrorBox(true);
        validation.setErrorStyle(DataValidation.ErrorStyle.STOP);
        validation.createErrorBox("Übernehmen", "Bitte Ja oder Nein wählen.");
        validation.setSuppressDropDownArrow(true);
        sheet.addValidationData(validation);
    }

    private void writeLegend(int startRow) {
        XSSFCellStyle title = workbook.createCellStyle();
        title.setFont(font(true, false, 12, DARK));
        XSSFCell titleCell = sheet.createRow(startRow).createCell(1);
        titleCell.setCellValue("Legende: Inspire-Datentypen");
        titleCell.setCellStyle(title);

        XSSFCellStyle head = style(DARK);
        head.setFont(font(true, false, 10, "FFFFFF"));
        XSSFRow headRow = sheet.createRow(startRow + 1);
        String[] heads = {"Typ", "Bedeutung", "Abgeleitet aus"};
        for (int c = 1; c <= 6; c++) {
            headRow.createCell(c).setCellStyle(head);
        }
        headRow.getCell(1).setCellValue(heads[0]);
        headRow.getCell(2).setCellValue(heads[1]);
        headRow.getCell(3).setCellValue(heads[2]);
        sheet.addMergedRegion(new CellRangeAddress(startRow + 1, startRow + 1, 3, 6));

        XSSFCellStyle text = workbook.createCellStyle();
        text.setBorderBottom(BorderStyle.HAIR);
        text.setBottomBorderColor(color("BFBFBF"));
        int row = startRow + 2;
        for (InspireType type : InspireType.values()) {
            XSSFCellStyle chip = style(TYPE_COLORS.get(type));
            chip.setFont(font(true, false, 10, "000000"));
            chip.setAlignment(HorizontalAlignment.CENTER);
            chip.setBorderBottom(BorderStyle.HAIR);
            chip.setBottomBorderColor(color("BFBFBF"));
            XSSFRow r = sheet.createRow(row);
            set(r, 1, type.inspireName(), chip);
            set(r, 2, type.meaning(), text);
            for (int c = 3; c <= 6; c++) {
                r.createCell(c).setCellStyle(text);
            }
            r.getCell(3).setCellValue(type.derivedFrom());
            sheet.addMergedRegion(new CellRangeAddress(row, row, 3, 6));
            row++;
        }
    }

    /** Geschaetzte Zeilenzahl der umbrochenen Spalten, damit Excel die Zeile nicht abschneidet. */
    private static int lines(ModelField field) {
        int notes = 0;
        for (String note : field.notes()) {
            notes += wrappedLines(note, WIDTHS[COL_NOTES]);
        }
        int sources = 0;
        for (String source : field.sources()) {
            sources += wrappedLines(source, WIDTHS[11]);
        }
        int values = wrappedLines(String.join(" | ", field.values()), WIDTHS[6]);
        return Math.max(1, Math.max(notes, Math.max(sources, values)));
    }

    private static int wrappedLines(String text, int width) {
        return text.isEmpty() ? 0 : (text.length() + width - 3) / (width - 2);
    }

    private static void set(XSSFRow row, int column, Object value, XSSFCellStyle style) {
        XSSFCell cell = row.createCell(column);
        cell.setCellStyle(style);
        if (value instanceof Integer number) {
            cell.setCellValue(number);
        } else if (value instanceof Double number) {
            cell.setCellValue(number);
        } else if (value != null && !value.toString().isEmpty()) {
            cell.setCellValue(value.toString());
        }
    }

    private XSSFCellStyle style(String fill) {
        XSSFCellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(color(fill));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private XSSFFont font(boolean bold, boolean italic, int size, String rgb) {
        XSSFFont font = workbook.createFont();
        font.setBold(bold);
        font.setItalic(italic);
        font.setFontHeightInPoints((short) size);
        font.setColor(color(rgb));
        return font;
    }

    private XSSFFont monospace() {
        XSSFFont font = workbook.createFont();
        font.setFontName("Consolas");
        font.setFontHeightInPoints((short) 9);
        font.setColor(color("44546A"));
        return font;
    }

    private static XSSFColor color(String rgb) {
        return new XSSFColor(HexFormat.of().parseHex(rgb));
    }
}
