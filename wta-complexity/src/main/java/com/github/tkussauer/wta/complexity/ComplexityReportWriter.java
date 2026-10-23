package com.github.tkussauer.wta.complexity;

import com.github.tkussauer.wta.common.ExcelSheetWriter;
import com.github.tkussauer.wta.fonts.ExtractionFailure;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;

/**
 * Schreibt das Ergebnis der Komplexitaetsbewertung in eine Excel-Datei.
 * <ul>
 *     <li><b>Komplexität</b> – T-Shirt-Größe, Punkte, Treiber und Kennzahlen je Vorlage</li>
 *     <li><b>Übersicht</b> – Verteilung der Vorlagen auf S, M, L, XL</li>
 *     <li><b>Punkte</b> – Punkte je Kennzahl und Vorlage</li>
 *     <li><b>Bewertungsschema</b> – verwendete Gewichte und Mindestgrößen</li>
 *     <li><b>Fehler</b> – nicht lesbare Dokumente (nur falls vorhanden)</li>
 * </ul>
 */
public class ComplexityReportWriter {

    public static final String SHEET_COMPLEXITY = "Komplexität";
    public static final String SHEET_SUMMARY = "Übersicht";
    public static final String SHEET_POINTS = "Punkte";
    public static final String SHEET_MODEL = "Bewertungsschema";
    public static final String SHEET_ERRORS = "Fehler";

    /** Spalte der T-Shirt-Groesse in den Blaettern Komplexität und Punkte. */
    private static final int SIZE_COLUMN = 2;

    private static final Map<TShirtSize, String> SIZE_COLORS = Map.of(
            TShirtSize.S, "C6EFCE",
            TShirtSize.M, "FFEB9C",
            TShirtSize.L, "FFCC99",
            TShirtSize.XL, "FFC7CE");

    private static final Comparator<Assessment> ORDER = Comparator
            .comparing(Assessment::size).reversed()
            .thenComparing(Comparator.comparingDouble(Assessment::totalPoints).reversed())
            .thenComparing(a -> a.metrics().document(), String.CASE_INSENSITIVE_ORDER);

    public void write(ComplexityAnalysis.Result result, Path output) throws IOException {
        List<Assessment> assessments = new ArrayList<>(result.assessments());
        assessments.sort(ORDER);
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Map<TShirtSize, CellStyle> sizeStyles = createSizeStyles(workbook);
            writeComplexity(workbook, assessments, sizeStyles);
            writeSummary(workbook, result.model(), assessments, sizeStyles);
            writePoints(workbook, assessments, sizeStyles);
            writeModel(workbook, result.model());
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

    private void writeComplexity(XSSFWorkbook workbook, List<Assessment> assessments,
                                 Map<TShirtSize, CellStyle> sizeStyles) {
        List<String> headers = new ArrayList<>(List.of(
                "Dokument", "Format", "Größe", "Einstufung", "Punkte", "Hauptkomplexitätstreiber", "Angehoben durch"));
        for (Metric metric : Metric.values()) {
            headers.add(metric.label());
        }
        ExcelSheetWriter sheet = new ExcelSheetWriter(workbook, SHEET_COMPLEXITY, headers);
        for (Assessment assessment : assessments) {
            TemplateMetrics metrics = assessment.metrics();
            // Arrays.asList statt List.of, da "Angehoben durch" leer sein kann
            List<Object> values = new ArrayList<>(Arrays.asList(
                    metrics.document(), metrics.format(), assessment.size().name(), assessment.size().description(),
                    round(assessment.totalPoints()), drivers(assessment), minimumRules(assessment)));
            for (Metric metric : Metric.values()) {
                values.add(metrics.get(metric));
            }
            Row row = sheet.addRow(values.toArray());
            row.getCell(SIZE_COLUMN).setCellStyle(sizeStyles.get(assessment.size()));
        }
        sheet.finish();
    }

    private void writeSummary(XSSFWorkbook workbook, ComplexityModel model, List<Assessment> assessments,
                              Map<TShirtSize, CellStyle> sizeStyles) {
        Map<TShirtSize, List<Assessment>> bySize = new EnumMap<>(TShirtSize.class);
        for (TShirtSize size : TShirtSize.values()) {
            bySize.put(size, new ArrayList<>());
        }
        assessments.forEach(a -> bySize.get(a.size()).add(a));

        ExcelSheetWriter sheet = new ExcelSheetWriter(workbook, SHEET_SUMMARY, List.of(
                "Größe", "Einstufung", "Punktebereich", "Anzahl Vorlagen", "Anteil (%)", "Ø Punkte", "Ø Seiten",
                "Häufigste Treiber"));
        TShirtSize[] sizes = TShirtSize.values();
        for (int i = 0; i < sizes.length; i++) {
            TShirtSize size = sizes[i];
            List<Assessment> group = bySize.get(size);
            String range = i + 1 < sizes.length
                    ? format(model.threshold(size)) + " bis < " + format(model.threshold(sizes[i + 1]))
                    : "ab " + format(model.threshold(size));
            Row row = sheet.addRow(size.name(), size.description(), range, group.size(),
                    assessments.isEmpty() ? 0.0 : round(100.0 * group.size() / assessments.size()),
                    group.isEmpty() ? null : round(average(group, Assessment::totalPoints)),
                    group.isEmpty() ? null : round(average(group, a -> a.metrics().get(Metric.PAGES))),
                    frequentDrivers(group));
            row.getCell(0).setCellStyle(sizeStyles.get(size));
        }
        sheet.addRow("Gesamt", null, null, assessments.size(), assessments.isEmpty() ? 0.0 : 100.0,
                assessments.isEmpty() ? null : round(average(assessments, Assessment::totalPoints)),
                assessments.isEmpty() ? null : round(average(assessments, a -> a.metrics().get(Metric.PAGES))),
                frequentDrivers(assessments));
        sheet.finish();
    }

    private void writePoints(XSSFWorkbook workbook, List<Assessment> assessments,
                             Map<TShirtSize, CellStyle> sizeStyles) {
        List<String> headers = new ArrayList<>(List.of("Dokument", "Format", "Größe", "Punkte gesamt"));
        for (Metric metric : Metric.values()) {
            headers.add(metric.label());
        }
        ExcelSheetWriter sheet = new ExcelSheetWriter(workbook, SHEET_POINTS, headers);
        for (Assessment assessment : assessments) {
            List<Object> values = new ArrayList<>(List.of(assessment.metrics().document(),
                    assessment.metrics().format(), assessment.size().name(), round(assessment.totalPoints())));
            for (Metric metric : Metric.values()) {
                values.add(round(assessment.points().get(metric)));
            }
            Row row = sheet.addRow(values.toArray());
            row.getCell(SIZE_COLUMN).setCellStyle(sizeStyles.get(assessment.size()));
        }
        sheet.finish();
    }

    private void writeModel(XSSFWorkbook workbook, ComplexityModel model) {
        ExcelSheetWriter sheet = new ExcelSheetWriter(workbook, SHEET_MODEL, List.of(
                "Kennzahl", "Schlüssel", "Beschreibung", "Gewicht (Punkte je Vorkommen)", "Mindestgröße"));
        for (Metric metric : Metric.values()) {
            TShirtSize minimum = model.minimumSize(metric);
            sheet.addRow(metric.label(), metric.key(), metric.description(), model.weight(metric),
                    minimum != null ? minimum.name() : null);
        }
        sheet.finish();
    }

    private void writeFailures(XSSFWorkbook workbook, List<ExtractionFailure> failures) {
        ExcelSheetWriter sheet = new ExcelSheetWriter(workbook, SHEET_ERRORS, List.of("Dokument", "Fehlermeldung"));
        failures.forEach(f -> sheet.addRow(f.document(), f.message()));
        sheet.finish();
    }

    private static String drivers(Assessment assessment) {
        return assessment.drivers().stream()
                .map(m -> m.label() + " (" + assessment.metrics().get(m) + ")")
                .collect(Collectors.joining(", "));
    }

    private static String minimumRules(Assessment assessment) {
        if (assessment.minimumRules().isEmpty()) {
            return null;
        }
        return assessment.minimumRules().stream().map(Metric::label).collect(Collectors.joining(", "))
                + " (nach Punkten: " + assessment.sizeByPoints().name() + ")";
    }

    private static String frequentDrivers(List<Assessment> group) {
        Map<Metric, Long> counts = group.stream()
                .flatMap(a -> a.drivers().stream())
                .collect(Collectors.groupingBy(m -> m, () -> new EnumMap<>(Metric.class), Collectors.counting()));
        return counts.entrySet().stream()
                .sorted(Map.Entry.<Metric, Long>comparingByValue(Comparator.reverseOrder()))
                .limit(3)
                .map(e -> e.getKey().label())
                .collect(Collectors.joining(", "));
    }

    private static double average(List<Assessment> group, ToDoubleFunction<Assessment> value) {
        return group.stream().mapToDouble(value).average().orElse(0);
    }

    private static double round(double value) {
        return Math.round(value * 100) / 100.0;
    }

    private static String format(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }

    private static Map<TShirtSize, CellStyle> createSizeStyles(XSSFWorkbook workbook) {
        Map<TShirtSize, CellStyle> styles = new EnumMap<>(TShirtSize.class);
        XSSFFont bold = workbook.createFont();
        bold.setBold(true);
        for (TShirtSize size : TShirtSize.values()) {
            XSSFCellStyle style = workbook.createCellStyle();
            style.setFillForegroundColor(new XSSFColor(HexFormat.of().parseHex(SIZE_COLORS.get(size))));
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            style.setAlignment(HorizontalAlignment.CENTER);
            style.setFont(bold);
            styles.put(size, style);
        }
        return styles;
    }
}
