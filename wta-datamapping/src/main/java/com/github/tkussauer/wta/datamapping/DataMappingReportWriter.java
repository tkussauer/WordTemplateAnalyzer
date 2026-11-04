package com.github.tkussauer.wta.datamapping;

import com.github.tkussauer.wta.common.ExcelSheetWriter;
import com.github.tkussauer.wta.common.ExtractionFailure;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Schreibt die extrahierten Variablen in eine Excel-Datei.
 * <ul>
 *     <li><b>DataModel</b> – Vorschlag fuer ein konsolidiertes Inspire-Datenmodell ohne Dubletten</li>
 *     <li><b>Variablen</b> – jede Variable einmal, mit Datentyp, Verwendung und leeren Mapping-Spalten</li>
 *     <li><b>Vorkommen</b> – jedes einzelne Vorkommen mit Bereich, Feldcode, Bedingung und Kontext</li>
 *     <li><b>Vorlagen</b> – Anzahl Variablen je Vorlage und Kategorie</li>
 *     <li><b>Fehler</b> – nicht lesbare Dokumente (nur falls vorhanden)</li>
 * </ul>
 */
public class DataMappingReportWriter {

    public static final String SHEET_DATA_MODEL = DataModelSheetWriter.SHEET_NAME;
    public static final String SHEET_VARIABLES = "Variablen";
    public static final String SHEET_OCCURRENCES = "Vorkommen";
    public static final String SHEET_TEMPLATES = "Vorlagen";
    public static final String SHEET_ERRORS = "Fehler";

    /** Spalten zum Ausfuellen fuer das Mapping auf das Zielsystem. */
    static final List<String> MAPPING_COLUMNS = List.of("Ziel-Datenfeld", "Datenquelle", "Bemerkung");

    private static final class Summary {
        final VariableOccurrence first;
        final Set<String> fieldTypes = new TreeSet<>();
        final Set<String> dataTypes = new LinkedHashSet<>();
        final Set<String> formats = new LinkedHashSet<>();
        final Set<String> options = new LinkedHashSet<>();
        final Set<String> values = new LinkedHashSet<>();
        final Set<String> bindings = new LinkedHashSet<>();
        final Set<String> documents = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        boolean inCondition;
        boolean usedInText;
        int count;
        String context;

        Summary(VariableOccurrence first) {
            this.first = first;
        }
    }

    public void write(DataMappingAnalysis.Result result, Path output) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            CellStyle mappingHeader = createMappingHeaderStyle(workbook);
            writeVariables(workbook, result.templates(), mappingHeader);
            writeOccurrences(workbook, result.templates());
            writeTemplates(workbook, result.templates());
            if (!result.failures().isEmpty()) {
                writeFailures(workbook, result.failures());
            }
            new DataModelSheetWriter(workbook).write(DataModelBuilder.build(result.templates()),
                    result.templates().size());
            // Das Datenmodell ist das Hauptergebnis: als erstes, aktives Blatt
            workbook.setSheetOrder(DataModelSheetWriter.SHEET_NAME, 0);
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                // sonst oeffnet Excel mehrere ausgewaehlte Blaetter als Gruppe
                workbook.getSheetAt(i).setSelected(i == 0);
            }
            workbook.setActiveSheet(0);
            Path parent = output.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (OutputStream out = Files.newOutputStream(output)) {
                workbook.write(out);
            }
        }
    }

    private void writeVariables(XSSFWorkbook workbook, List<TemplateVariables> templates, CellStyle mappingHeader) {
        Map<String, Summary> summaries = new LinkedHashMap<>();
        for (TemplateVariables template : templates) {
            for (VariableOccurrence occurrence : template.occurrences()) {
                Summary summary = summaries.computeIfAbsent(occurrence.key(), k -> new Summary(occurrence));
                summary.count++;
                summary.documents.add(template.document());
                summary.fieldTypes.add(occurrence.fieldType());
                addIfPresent(summary.dataTypes, occurrence.dataType());
                addIfPresent(summary.formats, occurrence.format());
                addIfPresent(summary.options, occurrence.options());
                addIfPresent(summary.values, occurrence.value());
                addIfPresent(summary.bindings, occurrence.binding());
                summary.inCondition |= occurrence.inCondition();
                summary.usedInText |= !occurrence.isDefinition();
                if (summary.context == null && occurrence.context() != null && !occurrence.context().isEmpty()) {
                    summary.context = occurrence.context();
                }
            }
        }
        List<Summary> sorted = new ArrayList<>(summaries.values());
        sorted.sort(Comparator.<Summary, VariableKind>comparing(s -> s.first.kind())
                .thenComparing(s -> s.first.name(), String.CASE_INSENSITIVE_ORDER));

        List<String> headers = new ArrayList<>(List.of("Nr.", "Variable", "Kategorie", "Feldarten", "Datentyp",
                "Format", "Auswahlwerte", "Standardwert/Wert", "Datenbindung (XPath)", "Innerhalb IF-Feld",
                "Im Text verwendet", "Vorkommen", "Anzahl Vorlagen", "Vorlagen", "Beispielkontext"));
        int firstMappingColumn = headers.size();
        headers.addAll(MAPPING_COLUMNS);
        ExcelSheetWriter sheet = new ExcelSheetWriter(workbook, SHEET_VARIABLES, headers);
        int nr = 0;
        for (Summary s : sorted) {
            sheet.addRow(++nr, s.first.name(), s.first.kind().label(), String.join(", ", s.fieldTypes),
                    join(dataTypes(s.dataTypes)), join(s.formats), join(s.options), join(s.values), join(s.bindings),
                    s.inCondition, s.usedInText, s.count, s.documents.size(), String.join(", ", s.documents),
                    s.context);
        }
        sheet.finish();
        Row header = workbook.getSheet(SHEET_VARIABLES).getRow(0);
        for (int i = firstMappingColumn; i < headers.size(); i++) {
            Cell cell = header.getCell(i);
            cell.setCellStyle(mappingHeader);
            workbook.getSheet(SHEET_VARIABLES).setColumnWidth(i, Math.max(
                    workbook.getSheet(SHEET_VARIABLES).getColumnWidth(i), 25 * 256));
        }
    }

    private void writeOccurrences(XSSFWorkbook workbook, List<TemplateVariables> templates) {
        ExcelSheetWriter sheet = new ExcelSheetWriter(workbook, SHEET_OCCURRENCES, List.of(
                "Vorlage", "Bereich", "Kategorie", "Feldart", "Variable", "Datentyp", "Format", "Auswahlwerte",
                "Standardwert/Wert", "Datenbindung (XPath)", "Innerhalb IF-Feld", "Bedingung", "Feldcode",
                "Kontext"));
        for (TemplateVariables template : templates) {
            for (VariableOccurrence o : template.occurrences()) {
                // Arrays.asList statt List.of, da viele Werte leer sein koennen
                sheet.addRow(Arrays.asList(template.document(), o.area(), o.kind().label(), o.fieldType(),
                        o.name(), o.dataType(), o.format(), o.options(), o.value(), o.binding(), o.inCondition(),
                        o.condition(), o.fieldCode(), o.context()).toArray());
            }
        }
        sheet.finish();
    }

    private void writeTemplates(XSSFWorkbook workbook, List<TemplateVariables> templates) {
        List<String> headers = new ArrayList<>(List.of("Vorlage", "Format", "Variablen gesamt"));
        for (VariableKind kind : VariableKind.values()) {
            headers.add(kind.label());
        }
        headers.add("Innerhalb IF-Feld");
        headers.add("Vorkommen");
        ExcelSheetWriter sheet = new ExcelSheetWriter(workbook, SHEET_TEMPLATES, headers);
        for (TemplateVariables template : templates) {
            Map<VariableKind, Set<String>> byKind = new EnumMap<>(VariableKind.class);
            Set<String> conditional = new TreeSet<>();
            for (VariableOccurrence o : template.occurrences()) {
                byKind.computeIfAbsent(o.kind(), k -> new TreeSet<>()).add(o.key());
                if (o.inCondition()) {
                    conditional.add(o.key());
                }
            }
            List<Object> values = new ArrayList<>(List.of(template.document(), template.format(),
                    byKind.values().stream().mapToInt(Set::size).sum()));
            for (VariableKind kind : VariableKind.values()) {
                values.add(byKind.getOrDefault(kind, Set.of()).size());
            }
            values.add(conditional.size());
            values.add(template.occurrences().size());
            sheet.addRow(values.toArray());
        }
        sheet.finish();
    }

    private void writeFailures(XSSFWorkbook workbook, List<ExtractionFailure> failures) {
        ExcelSheetWriter sheet = new ExcelSheetWriter(workbook, SHEET_ERRORS, List.of("Dokument", "Fehlermeldung"));
        failures.forEach(f -> sheet.addRow(f.document(), f.message()));
        sheet.finish();
    }

    private static void addIfPresent(Set<String> set, String value) {
        if (value != null && !value.isBlank()) {
            set.add(value);
        }
    }

    /** Ein vermuteter Datentyp entfaellt, wenn derselbe Typ anderswo sicher bekannt ist. */
    private static Set<String> dataTypes(Set<String> types) {
        Set<String> result = new LinkedHashSet<>(types);
        result.removeIf(t -> t.endsWith(DataTypes.GUESSED)
                && types.contains(t.substring(0, t.length() - DataTypes.GUESSED.length())));
        return result;
    }

    private static String join(Set<String> values) {
        return values.isEmpty() ? null : String.join("; ", values);
    }

    private static CellStyle createMappingHeaderStyle(XSSFWorkbook workbook) {
        XSSFFont bold = workbook.createFont();
        bold.setBold(true);
        XSSFCellStyle style = workbook.createCellStyle();
        style.setFont(bold);
        style.setFillForegroundColor(new XSSFColor(HexFormat.of().parseHex("FFEB9C")));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }
}
