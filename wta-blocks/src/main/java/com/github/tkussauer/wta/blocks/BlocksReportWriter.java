package com.github.tkussauer.wta.blocks;

import com.github.tkussauer.wta.common.ExcelSheetWriter;
import com.github.tkussauer.wta.common.ExtractionFailure;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.ConditionalFormattingThreshold.RangeType;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFColorScaleFormatting;
import org.apache.poi.xssf.usermodel.XSSFConditionalFormattingRule;
import org.apache.poi.xssf.usermodel.XSSFDataBarFormatting;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFSheetConditionalFormatting;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Schreibt gleiche Bloecke und aehnliche Vorlagen in eine Excel-Datei.
 * <ul>
 *     <li><b>Blöcke</b> – Kandidaten fuer externe Bloecke in Inspire, nach Wiederverwendungswert sortiert</li>
 *     <li><b>Block-Vorkommen</b> – wo jeder Block vorkommt</li>
 *     <li><b>Ähnliche Dokumente</b> – Vorlagenpaare ab der Aehnlichkeitsschwelle</li>
 *     <li><b>Ähnlichkeitsgruppen</b> – Vorlagen, die sich zu einer Vorlage zusammenfassen lassen</li>
 *     <li><b>Matrix</b> – Aehnlichkeit aller Vorlagen untereinander (bis {@value #MAX_MATRIX} Vorlagen)</li>
 *     <li><b>Übersicht</b>, <b>Parameter</b>, <b>Fehler</b></li>
 * </ul>
 */
public class BlocksReportWriter {

    public static final String SHEET_BLOCKS = "Blöcke";
    public static final String SHEET_OCCURRENCES = "Block-Vorkommen";
    public static final String SHEET_PAIRS = "Ähnliche Dokumente";
    public static final String SHEET_GROUPS = "Ähnlichkeitsgruppen";
    public static final String SHEET_MATRIX = "Matrix";
    public static final String SHEET_SUMMARY = "Übersicht";
    public static final String SHEET_SETTINGS = "Parameter";
    public static final String SHEET_ERRORS = "Fehler";

    static final int MAX_MATRIX = 200;
    private static final int TEXT_WIDTH = 80;
    private static final int PREVIEW_WIDTH = 60;

    public void write(BlocksAnalysis.Result result, Path output) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Styles styles = new Styles(workbook);
            List<Similarity.Group> groups = result.similarity().groups(result.settings().threshold());
            writeBlocks(workbook, styles, result);
            writeOccurrences(workbook, result.blocks());
            writePairs(workbook, styles, result, groups);
            writeGroups(workbook, styles, result, groups);
            if (result.templates().size() >= 2 && result.templates().size() <= MAX_MATRIX) {
                writeMatrix(workbook, result);
            }
            writeSummary(workbook, result, groups);
            writeSettings(workbook, result.settings());
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

    private void writeBlocks(XSSFWorkbook workbook, Styles styles, BlocksAnalysis.Result result) {
        int templates = result.templates().size();
        ExcelSheetWriter sheet = new ExcelSheetWriter(workbook, SHEET_BLOCKS, List.of("Nr.", "Blockname (Vorschlag)",
                "Bereich", "Absätze", "Buchstaben", "Vorlagen", "Anteil Vorlagen (%)", "Wiederverwendungswert",
                "Felder im Block", "Text", "Vorlagen (Namen)"));
        List<Row> rows = new ArrayList<>();
        for (Block block : result.blocks()) {
            rows.add(sheet.addRow(block.id(), block.name(), String.join(", ", block.areas()), block.paragraphs().size(),
                    block.letters(), block.documents().size(), percent(block.documents().size(), templates),
                    block.reuseValue(), String.join(" ", block.fields()), String.join("\n", block.paragraphs()),
                    String.join(", ", block.documents())));
        }
        sheet.finish();
        XSSFSheet xssf = workbook.getSheet(SHEET_BLOCKS);
        xssf.setColumnWidth(9, TEXT_WIDTH * 256);
        xssf.setColumnWidth(10, PREVIEW_WIDTH * 256);
        for (Row row : rows) {
            styles.topAligned(row);
            styles.wrap(row.getCell(9));
            styles.wrap(row.getCell(10));
            row.getCell(1).setCellStyle(styles.bold);
            row.setHeightInPoints(styles.height(row.getCell(9).getStringCellValue(), TEXT_WIDTH));
        }
        if (!rows.isEmpty()) {
            dataBar(xssf, new CellRangeAddress(1, rows.size(), 7, 7), "63BE7B");
            dataBar(xssf, new CellRangeAddress(1, rows.size(), 6, 6), "5B9BD5");
        }
        xssf.createFreezePane(2, 1);
    }

    private void writeOccurrences(XSSFWorkbook workbook, List<Block> blocks) {
        ExcelSheetWriter sheet = new ExcelSheetWriter(workbook, SHEET_OCCURRENCES, List.of("Block", "Blockname (Vorschlag)",
                "Vorlage", "Bereich", "Ab Absatz", "Bis Absatz"));
        for (Block block : blocks) {
            for (Block.Occurrence o : block.occurrences()) {
                sheet.addRow(block.id(), block.name(), o.document(), o.area(), o.from(), o.to());
            }
        }
        sheet.finish();
    }

    private void writePairs(XSSFWorkbook workbook, Styles styles, BlocksAnalysis.Result result,
                            List<Similarity.Group> groups) {
        Map<Integer, Integer> groupOf = groupIndex(groups);
        List<TemplateText> templates = result.templates();
        ExcelSheetWriter sheet = new ExcelSheetWriter(workbook, SHEET_PAIRS, List.of("Vorlage A", "Vorlage B",
                "Ähnlichkeit (%)", "Gemeinsame Absätze", "Nur in A", "Nur in B", "Abweichungen in A",
                "Abweichungen in B", "Gruppe"));
        List<Row> rows = new ArrayList<>();
        for (Similarity.Pair pair : result.similarity().pairs(result.settings().threshold())) {
            rows.add(sheet.addRow(templates.get(pair.a()).document(), templates.get(pair.b()).document(),
                    round(pair.similarity() * 100), pair.common(), pair.onlyA(), pair.onlyB(), pair.previewA(),
                    pair.previewB(), "G" + groupOf.get(pair.a())));
        }
        sheet.finish();
        XSSFSheet xssf = workbook.getSheet(SHEET_PAIRS);
        xssf.setColumnWidth(6, PREVIEW_WIDTH * 256);
        xssf.setColumnWidth(7, PREVIEW_WIDTH * 256);
        for (Row row : rows) {
            styles.topAligned(row);
            styles.wrap(row.getCell(6));
            styles.wrap(row.getCell(7));
            row.setHeightInPoints(Math.max(styles.height(row.getCell(6).getStringCellValue(), PREVIEW_WIDTH),
                    styles.height(row.getCell(7).getStringCellValue(), PREVIEW_WIDTH)));
        }
        if (!rows.isEmpty()) {
            colorScale(xssf, new CellRangeAddress(1, rows.size(), 2, 2), result.settings().threshold() * 100);
        }
        xssf.createFreezePane(2, 1);
    }

    private void writeGroups(XSSFWorkbook workbook, Styles styles, BlocksAnalysis.Result result,
                             List<Similarity.Group> groups) {
        List<TemplateText> templates = result.templates();
        ExcelSheetWriter sheet = new ExcelSheetWriter(workbook, SHEET_GROUPS, List.of("Gruppe", "Vorlagen",
                "Referenzvorlage (Vorschlag)", "Min. Ähnlichkeit (%)", "Ø Ähnlichkeit (%)", "Vorlagen (Namen)"));
        List<Row> rows = new ArrayList<>();
        for (Similarity.Group group : groups) {
            List<String> names = new ArrayList<>();
            group.members().forEach(m -> names.add(templates.get(m).document()));
            rows.add(sheet.addRow("G" + group.number(), group.members().size(),
                    templates.get(group.reference()).document(), round(group.min() * 100),
                    round(group.average() * 100), String.join("\n", names)));
        }
        sheet.finish();
        XSSFSheet xssf = workbook.getSheet(SHEET_GROUPS);
        xssf.setColumnWidth(5, PREVIEW_WIDTH * 256);
        for (Row row : rows) {
            styles.topAligned(row);
            styles.wrap(row.getCell(5));
            row.getCell(2).setCellStyle(styles.bold);
            row.setHeightInPoints(styles.height(row.getCell(5).getStringCellValue(), PREVIEW_WIDTH));
        }
    }

    /** Aehnlichkeit aller Vorlagen untereinander als Farbmatrix. */
    private void writeMatrix(XSSFWorkbook workbook, BlocksAnalysis.Result result) {
        List<TemplateText> templates = result.templates();
        int n = templates.size();
        XSSFSheet sheet = workbook.createSheet(SHEET_MATRIX);
        XSSFCellStyle header = workbook.createCellStyle();
        XSSFFont bold = workbook.createFont();
        bold.setBold(true);
        header.setFont(bold);
        header.setRotation((short) 90);
        header.setVerticalAlignment(VerticalAlignment.BOTTOM);
        header.setAlignment(HorizontalAlignment.CENTER);
        XSSFCellStyle rowHeader = workbook.createCellStyle();
        rowHeader.setFont(bold);
        XSSFCellStyle value = workbook.createCellStyle();
        value.setAlignment(HorizontalAlignment.CENTER);
        value.setDataFormat(workbook.createDataFormat().getFormat("0"));
        XSSFCellStyle diagonal = workbook.createCellStyle();
        diagonal.cloneStyleFrom(value);
        diagonal.setFillForegroundColor(color("D9D9D9"));
        diagonal.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        XSSFRow top = sheet.createRow(0);
        XSSFCell corner = top.createCell(0);
        corner.setCellValue("Ähnlichkeit in %");
        corner.setCellStyle(rowHeader);
        int longest = 10;
        for (int b = 0; b < n; b++) {
            XSSFCell cell = top.createCell(b + 1);
            cell.setCellValue(templates.get(b).document());
            cell.setCellStyle(header);
            sheet.setColumnWidth(b + 1, 5 * 256);
            longest = Math.max(longest, templates.get(b).document().length());
        }
        sheet.setColumnWidth(0, Math.min(60, longest + 2) * 256);
        // Gedrehte Spaltenkoepfe: Hoehe nach dem laengsten Vorlagennamen
        top.setHeightInPoints(Math.min(409, 7f * longest + 10));
        for (int a = 0; a < n; a++) {
            XSSFRow row = sheet.createRow(a + 1);
            XSSFCell name = row.createCell(0);
            name.setCellValue(templates.get(a).document());
            name.setCellStyle(rowHeader);
            for (int b = 0; b < n; b++) {
                XSSFCell cell = row.createCell(b + 1);
                if (a == b) {
                    cell.setCellStyle(diagonal);
                } else {
                    cell.setCellValue(Math.round(result.similarity().get(a, b) * 100));
                    cell.setCellStyle(value);
                }
            }
        }
        CellRangeAddress values = new CellRangeAddress(1, n, 1, n);
        colorScale(sheet, values, result.settings().threshold() * 100);
        // Werte ab der Schwelle fett und umrandet hervorheben
        String first = CellReference.convertNumToColString(1) + "2";
        XSSFConditionalFormattingRule high = sheet.getSheetConditionalFormatting().createConditionalFormattingRule(
                "AND(ISNUMBER(" + first + ")," + first + ">=" + Math.round(result.settings().threshold() * 100) + ")");
        high.createFontFormatting().setFontStyle(false, true);
        sheet.getSheetConditionalFormatting().addConditionalFormatting(new CellRangeAddress[] {values}, high);
        sheet.createFreezePane(1, 1);
    }

    private void writeSummary(XSSFWorkbook workbook, BlocksAnalysis.Result result, List<Similarity.Group> groups) {
        Map<Integer, Integer> groupOf = groupIndex(groups);
        List<TemplateText> templates = result.templates();
        Map<String, Set<Integer>> covered = new HashMap<>();
        Map<String, Integer> blockCount = new HashMap<>();
        for (Block block : result.blocks()) {
            Set<String> counted = new HashSet<>();
            for (Block.Occurrence o : block.occurrences()) {
                Set<Integer> numbers = covered.computeIfAbsent(o.document(), d -> new HashSet<>());
                for (int i = o.from(); i <= o.to(); i++) {
                    numbers.add(i);
                }
                if (counted.add(o.document())) {
                    blockCount.merge(o.document(), 1, Integer::sum);
                }
            }
        }
        ExcelSheetWriter sheet = new ExcelSheetWriter(workbook, SHEET_SUMMARY, List.of("Vorlage", "Format", "Absätze",
                "Buchstaben", "Anteil in Blöcken (%)", "Blöcke", "Ähnlichste Vorlage", "Ähnlichkeit (%)", "Gruppe"));
        for (int i = 0; i < templates.size(); i++) {
            TemplateText template = templates.get(i);
            Set<Integer> numbers = covered.getOrDefault(template.document(), Set.of());
            int letters = 0;
            int coveredLetters = 0;
            for (Paragraph paragraph : template.paragraphs()) {
                letters += paragraph.letters();
                coveredLetters += numbers.contains(paragraph.number()) ? paragraph.letters() : 0;
            }
            int best = result.similarity().mostSimilar(i);
            Integer group = groupOf.get(i);
            sheet.addRow(template.document(), template.format(), template.paragraphs().size(), letters,
                    percent(coveredLetters, letters), blockCount.getOrDefault(template.document(), 0),
                    best >= 0 ? templates.get(best).document() : null,
                    best >= 0 ? round(result.similarity().get(i, best) * 100) : null,
                    group != null ? "G" + group : null);
        }
        sheet.finish();
        if (!templates.isEmpty()) {
            XSSFSheet xssf = workbook.getSheet(SHEET_SUMMARY);
            dataBar(xssf, new CellRangeAddress(1, templates.size(), 4, 4), "5B9BD5");
            colorScale(xssf, new CellRangeAddress(1, templates.size(), 7, 7), result.settings().threshold() * 100);
        }
    }

    private void writeSettings(XSSFWorkbook workbook, BlocksAnalysis.Settings settings) {
        ExcelSheetWriter sheet = new ExcelSheetWriter(workbook, SHEET_SETTINGS, List.of("Parameter", "Wert", "Bedeutung"));
        sheet.addRow("Ähnlichkeitsschwelle (%)", Math.round(settings.threshold() * 100),
                "Vorlagenpaare ab diesem Wert gelten als ähnlich (Dice-Koeffizient über Folgen von "
                        + Similarity.SHINGLE + " Wörtern)");
        sheet.addRow("Min. Buchstaben je Absatz", settings.minParagraphLetters(),
                "Kürzere Absätze (Leerzeilen, einzelne Felder) werden beim Blockvergleich übergangen");
        sheet.addRow("Min. Buchstaben je Block", settings.minBlockLetters(), "Kleinere gemeinsame Texte gelten nicht als Block");
        sheet.addRow("Min. Vorlagen je Block", settings.minDocuments(), "Ein Block muss in so vielen Vorlagen vorkommen");
        sheet.finish();
    }

    private static Map<Integer, Integer> groupIndex(List<Similarity.Group> groups) {
        Map<Integer, Integer> groupOf = new HashMap<>();
        for (Similarity.Group group : groups) {
            group.members().forEach(m -> groupOf.put(m, group.number()));
        }
        return groupOf;
    }

    /** Farbskala weiss - gelb (Schwelle) - gruen (100). */
    private static void colorScale(XSSFSheet sheet, CellRangeAddress range, double threshold) {
        XSSFSheetConditionalFormatting formatting = sheet.getSheetConditionalFormatting();
        XSSFConditionalFormattingRule rule = formatting.createConditionalFormattingColorScaleRule();
        XSSFColorScaleFormatting scale = rule.getColorScaleFormatting();
        scale.getThresholds()[0].setRangeType(RangeType.NUMBER);
        scale.getThresholds()[0].setValue(0d);
        scale.getThresholds()[1].setRangeType(RangeType.NUMBER);
        scale.getThresholds()[1].setValue(threshold);
        scale.getThresholds()[2].setRangeType(RangeType.NUMBER);
        scale.getThresholds()[2].setValue(100d);
        scale.setColors(new XSSFColor[] {color("FFFFFF"), color("FFEB84"), color("63BE7B")});
        formatting.addConditionalFormatting(new CellRangeAddress[] {range}, rule);
    }

    private static void dataBar(XSSFSheet sheet, CellRangeAddress range, String rgb) {
        XSSFSheetConditionalFormatting formatting = sheet.getSheetConditionalFormatting();
        XSSFConditionalFormattingRule rule = formatting.createConditionalFormattingRule(color(rgb));
        XSSFDataBarFormatting bar = rule.getDataBarFormatting();
        bar.getMinThreshold().setRangeType(RangeType.MIN);
        bar.getMaxThreshold().setRangeType(RangeType.MAX);
        formatting.addConditionalFormatting(new CellRangeAddress[] {range}, rule);
    }

    private static double percent(int part, int total) {
        return total == 0 ? 0.0 : round(100.0 * part / total);
    }

    private static double round(double value) {
        return Math.round(value * 10) / 10.0;
    }

    private static XSSFColor color(String rgb) {
        return new XSSFColor(HexFormat.of().parseHex(rgb));
    }

    /** Wiederkehrende Zellformate. */
    private static final class Styles {
        final XSSFCellStyle top;
        final XSSFCellStyle wrap;
        final XSSFCellStyle bold;
        private final XSSFWorkbook workbook;
        /** Oben ausgerichtete Kopien vorhandener Formate (z. B. Zahlenformat), je Formatindex. */
        private final Map<Short, XSSFCellStyle> topCopies = new HashMap<>();

        Styles(XSSFWorkbook workbook) {
            this.workbook = workbook;
            top = workbook.createCellStyle();
            top.setVerticalAlignment(VerticalAlignment.TOP);
            wrap = workbook.createCellStyle();
            wrap.cloneStyleFrom(top);
            wrap.setWrapText(true);
            bold = workbook.createCellStyle();
            bold.cloneStyleFrom(top);
            XSSFFont font = workbook.createFont();
            font.setBold(true);
            bold.setFont(font);
        }

        /** Oben ausrichten, Zahlenformate beibehalten. */
        void topAligned(Row row) {
            for (Cell cell : row) {
                if (cell.getCellType() == CellType.STRING) {
                    cell.setCellStyle(top);
                } else {
                    // gemeinsam genutzte Formate nicht veraendern, sondern oben ausgerichtete Kopie verwenden
                    XSSFCellStyle original = (XSSFCellStyle) cell.getCellStyle();
                    cell.setCellStyle(topCopies.computeIfAbsent(original.getIndex(), i -> {
                        XSSFCellStyle copy = workbook.createCellStyle();
                        copy.cloneStyleFrom(original);
                        copy.setVerticalAlignment(VerticalAlignment.TOP);
                        return copy;
                    }));
                }
            }
        }

        void wrap(Cell cell) {
            if (cell != null) {
                cell.setCellStyle(wrap);
            }
        }

        float height(String text, int width) {
            int lines = 0;
            for (String line : text.split("\n", -1)) {
                lines += Math.max(1, (line.length() + width - 3) / (width - 2));
            }
            return Math.min(409, 15f * Math.min(lines, 25) + 2);
        }
    }
}
