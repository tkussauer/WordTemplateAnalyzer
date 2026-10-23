package com.github.tkussauer.wta.common;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;

import java.util.List;

/**
 * Schreibt eine einfache Tabelle (Kopfzeile + Datenzeilen) in ein Excel-Arbeitsblatt.
 * Die Kopfzeile wird fixiert und mit Autofilter versehen.
 */
public final class ExcelSheetWriter {

    /** Maximale Zeichenanzahl einer Excel-Zelle. */
    public static final int MAX_CELL_LENGTH = 32_767;

    private static final int MAX_COLUMN_WIDTH = 80 * 256;

    private final Sheet sheet;
    private final CellStyle headerStyle;
    private final CellStyle decimalStyle;
    private final int columnCount;
    private int nextRow;

    public ExcelSheetWriter(Workbook workbook, String sheetName, List<String> headers) {
        this.sheet = workbook.createSheet(sheetName);
        this.headerStyle = createHeaderStyle(workbook);
        this.decimalStyle = workbook.createCellStyle();
        this.decimalStyle.setDataFormat(workbook.createDataFormat().getFormat("0.0#"));
        this.columnCount = headers.size();
        addRow(headers.toArray());
        sheet.getRow(0).forEach(cell -> cell.setCellStyle(headerStyle));
        sheet.createFreezePane(0, 1);
    }

    /**
     * Fuegt eine Datenzeile hinzu. Unterstuetzt werden {@link Number}, {@link Boolean}
     * und beliebige andere Objekte (als Text); {@code null} ergibt eine leere Zelle.
     */
    public void addRow(Object... values) {
        Row row = sheet.createRow(nextRow++);
        for (int i = 0; i < values.length; i++) {
            Object value = values[i];
            if (value == null) {
                continue;
            }
            Cell cell = row.createCell(i);
            if (value instanceof Integer || value instanceof Long) {
                cell.setCellValue(((Number) value).longValue());
            } else if (value instanceof Number number) {
                cell.setCellValue(number.doubleValue());
                cell.setCellStyle(decimalStyle);
            } else if (value instanceof Boolean bool) {
                cell.setCellValue(bool ? "Ja" : "Nein");
            } else {
                cell.setCellValue(truncate(value.toString()));
            }
        }
    }

    /**
     * Schliesst das Arbeitsblatt ab: Autofilter und Spaltenbreiten.
     */
    public void finish() {
        if (columnCount == 0) {
            return;
        }
        sheet.setAutoFilter(new CellRangeAddress(0, Math.max(0, nextRow - 1), 0, columnCount - 1));
        for (int i = 0; i < columnCount; i++) {
            sheet.autoSizeColumn(i);
            // Platz fuer den Autofilter-Pfeil
            int width = Math.min(sheet.getColumnWidth(i) + 3 * 256, MAX_COLUMN_WIDTH);
            sheet.setColumnWidth(i, width);
        }
    }

    private static String truncate(String text) {
        if (text.length() <= MAX_CELL_LENGTH) {
            return text;
        }
        return text.substring(0, MAX_CELL_LENGTH - 3) + "...";
    }

    private static CellStyle createHeaderStyle(Workbook workbook) {
        Font font = workbook.createFont();
        font.setBold(true);
        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        return style;
    }
}
