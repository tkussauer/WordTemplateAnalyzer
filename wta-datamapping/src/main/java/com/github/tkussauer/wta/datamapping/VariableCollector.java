package com.github.tkussauer.wta.datamapping;

import com.github.tkussauer.wta.common.FieldCode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sammelt Variablen aus einem Strom von Ereignissen (Text, Feldbeginn, Feldcode, Trenner,
 * Feldende, Absatzende), den die formatspezifischen Extraktoren erzeugen.
 * <p>
 * Ein Feld besteht aus Beginn, Feldcode, optional Trenner und Ergebnis sowie Ende. Verschachtelte
 * Felder erscheinen im Code des aeusseren Feldes als {@code {...}}.
 */
final class VariableCollector {

    private static final int CONTEXT_LENGTH = 120;
    private static final Set<String> CONDITION_KEYWORDS = Set.of("IF", "COMPARE", "SKIPIF", "NEXTIF");

    private static final class Field {
        final StringBuilder code = new StringBuilder();
        final FormFieldInfo form;
        boolean inCode = true;
        /** Vorkommen innerhalb dieses Feldes, die bei Bedingungen den Bedingungstext erhalten. */
        final List<VariableOccurrence> nested = new ArrayList<>();

        Field(FormFieldInfo form) {
            this.form = form;
        }

        String keyword() {
            return FieldCode.parse(code.toString()).keyword();
        }
    }

    private static final class Paragraph {
        /** Sichtbarer Text inkl. Feldergebnissen, fuer den Kontext. */
        final StringBuilder context = new StringBuilder();
        /** Text ausserhalb von Feldern, fuer die Platzhaltersuche. */
        final StringBuilder plain = new StringBuilder();
        final List<VariableOccurrence> pending = new ArrayList<>();
    }

    private final String document;
    private final List<Pattern> placeholders;
    private final boolean includeBookmarks;
    private final List<VariableOccurrence> occurrences = new ArrayList<>();
    private final Deque<Field> fields = new ArrayDeque<>();
    private final Deque<Paragraph> paragraphs = new ArrayDeque<>();
    private final Set<String> formFieldNames = new HashSet<>();
    /** Vorkommen ausserhalb eines Absatzes (z. B. Inhaltssteuerelement auf Blockebene). */
    private final List<VariableOccurrence> beforeParagraph = new ArrayList<>();
    private String area = "Hauptdokument";

    VariableCollector(String document, List<Pattern> placeholders, boolean includeBookmarks) {
        this.document = document;
        this.placeholders = placeholders;
        this.includeBookmarks = includeBookmarks;
    }

    /** Beginnt einen neuen Dokumentbereich (Hauptdokument, Kopfzeile, ...). */
    void area(String area) {
        flush();
        this.area = area;
    }

    void paragraphStart() {
        Paragraph paragraph = new Paragraph();
        // Kontext ist der erste folgende Absatz, z. B. der Inhalt des Inhaltssteuerelements
        paragraph.pending.addAll(beforeParagraph);
        beforeParagraph.clear();
        paragraphs.push(paragraph);
    }

    void paragraphEnd() {
        Paragraph paragraph = paragraphs.poll();
        if (paragraph == null) {
            return;
        }
        String plain = paragraph.plain.toString();
        for (Pattern pattern : placeholders) {
            Matcher matcher = pattern.matcher(plain);
            while (matcher.find()) {
                String name = (matcher.groupCount() > 0 ? matcher.group(1) : matcher.group()).strip();
                if (!name.isEmpty()) {
                    paragraph.pending.add(new VariableOccurrence(document, area, VariableKind.PLACEHOLDER,
                            matcher.group(), name).fieldCode(matcher.group()));
                }
            }
        }
        String context = shorten(paragraph.context.toString());
        for (VariableOccurrence occurrence : paragraph.pending) {
            if (occurrence.context() == null) {
                occurrence.context(context);
            }
            occurrences.add(occurrence);
        }
        // Text eines Textfeldes gehoert auch zum Kontext des umgebenden Absatzes
        Paragraph outer = paragraphs.peek();
        if (outer != null) {
            outer.context.append(' ').append(paragraph.context);
        }
    }

    void text(String text) {
        Paragraph paragraph = currentParagraph();
        boolean inCode = fields.stream().anyMatch(f -> f.inCode);
        if (fields.isEmpty()) {
            paragraph.plain.append(text);
        } else {
            // Feldergebnisse nicht nach Platzhaltern durchsuchen («Name» ist das Ergebnis eines MERGEFIELD)
            paragraph.plain.append(' ');
        }
        if (!inCode) {
            paragraph.context.append(text);
        }
    }

    void beginField(FormFieldInfo form) {
        fields.push(new Field(form));
    }

    void fieldCode(String text) {
        Field field = fields.peek();
        if (field != null && field.inCode) {
            field.code.append(text);
        }
    }

    void separateField() {
        Field field = fields.peek();
        if (field != null && field.inCode) {
            complete(field);
            field.inCode = false;
        }
    }

    void endField() {
        Field field = fields.poll();
        if (field == null) {
            return;
        }
        if (field.inCode) {
            complete(field);
        }
        Field parent = fields.peek();
        if (parent != null) {
            if (parent.inCode) {
                parent.code.append('{').append(field.code.toString().strip()).append('}');
            }
            parent.nested.addAll(field.nested);
        }
    }

    void bookmark(String name) {
        if (includeBookmarks && name != null && !name.isEmpty() && !name.startsWith("_")) {
            add(new VariableOccurrence(document, area, VariableKind.BOOKMARK, "Textmarke", name), null);
        }
    }

    void contentControl(VariableOccurrence occurrence) {
        add(occurrence, null);
    }

    /** Variable, die nur definiert ist (Dokumentvariable, benutzerdefinierte Eigenschaft). */
    void definition(VariableKind kind, String name, String value, String area) {
        VariableOccurrence occurrence = new VariableOccurrence(document, area, kind, VariableOccurrence.DEFINITION, name)
                .value(value);
        occurrences.add(occurrence);
    }

    String document() {
        return document;
    }

    String currentArea() {
        return area;
    }

    /** Schliesst offene Absaetze und entfernt Textmarken, die zu Formularfeldern gehoeren. */
    List<VariableOccurrence> finish() {
        flush();
        List<VariableOccurrence> result = new ArrayList<>(occurrences.size());
        for (VariableOccurrence occurrence : occurrences) {
            boolean formBookmark = occurrence.kind() == VariableKind.BOOKMARK
                    && "Textmarke".equals(occurrence.fieldType())
                    && formFieldNames.contains(occurrence.name().toLowerCase(Locale.ROOT));
            if (!formBookmark) {
                result.add(occurrence);
            }
        }
        return result;
    }

    private void flush() {
        while (!fields.isEmpty()) {
            endField();
        }
        while (!paragraphs.isEmpty()) {
            paragraphEnd();
        }
        occurrences.addAll(beforeParagraph);
        beforeParagraph.clear();
    }

    private Paragraph currentParagraph() {
        if (paragraphs.isEmpty()) {
            paragraphStart();
        }
        return paragraphs.peek();
    }

    /**
     * @param self  Feld, aus dem das Vorkommen stammt; zaehlt nicht als umgebendes Feld
     */
    private void add(VariableOccurrence occurrence, Field self) {
        Field parent = null;
        boolean nestedInCondition = false;
        for (Field field : fields) {
            if (field == self) {
                continue;
            }
            if (parent == null) {
                parent = field;
            }
            if (field.inCode && CONDITION_KEYWORDS.contains(field.keyword())) {
                nestedInCondition = true;
            }
        }
        occurrence.inCondition(nestedInCondition);
        if (parent != null) {
            parent.nested.add(occurrence);
        }
        if (paragraphs.isEmpty()) {
            beforeParagraph.add(occurrence);
        } else {
            paragraphs.peek().pending.add(occurrence);
        }
    }

    /** Wertet den vollstaendigen Feldcode aus. */
    private void complete(Field field) {
        String code = field.code.toString().strip();
        FieldCode parsed = FieldCode.parse(code);
        if (CONDITION_KEYWORDS.contains(parsed.keyword())) {
            for (VariableOccurrence nested : field.nested) {
                if (nested.condition() == null) {
                    nested.condition(code);
                }
            }
            return;
        }
        VariableOccurrence occurrence = toOccurrence(parsed, field.form);
        if (occurrence != null) {
            occurrence.fieldCode(code);
            add(occurrence, field);
        }
    }

    private VariableOccurrence toOccurrence(FieldCode code, FormFieldInfo form) {
        String keyword = code.keyword();
        String first = code.argument(0);
        VariableOccurrence occurrence = switch (keyword) {
            case "MERGEFIELD" -> named(VariableKind.MERGE_FIELD, keyword, first);
            case "DOCVARIABLE" -> named(VariableKind.DOC_VARIABLE, keyword, first);
            case "DOCPROPERTY" -> named(VariableKind.DOC_PROPERTY, keyword, first);
            case "ASK" -> named(VariableKind.PROMPT, keyword, first);
            case "FILLIN" -> named(VariableKind.PROMPT, keyword, first);
            case "SET" -> named(VariableKind.BOOKMARK, keyword, first);
            case "REF" -> named(VariableKind.BOOKMARK, keyword, first);
            case "FORMTEXT", "FORMCHECKBOX", "FORMDROPDOWN" -> formField(keyword, form);
            default -> null;
        };
        if (occurrence == null) {
            return null;
        }
        if ("ASK".equals(keyword)) {
            occurrence.options(nullIfEmpty(code.argument(1)));
        }
        if ("SET".equals(keyword)) {
            occurrence.value(nullIfEmpty(code.argument(1))).dataType("berechnet (SET)");
        }
        String defaultValue = code.switches().get("\\d");
        if (defaultValue != null && !defaultValue.isEmpty()) {
            occurrence.value(defaultValue);
        }
        applyFormatSwitches(code, occurrence);
        if (occurrence.dataType() == null) {
            occurrence.dataType(DataTypes.guess(occurrence.name()));
        }
        return occurrence;
    }

    private VariableOccurrence named(VariableKind kind, String keyword, String name) {
        if (name.isEmpty() || name.startsWith("{")) {
            // Name wird erst zur Laufzeit aus einem verschachtelten Feld gebildet
            name = name.isEmpty() ? null : "(dynamisch) " + name;
        }
        return name != null ? new VariableOccurrence(document, area, kind, keyword, name) : null;
    }

    private VariableOccurrence formField(String keyword, FormFieldInfo form) {
        String name = form != null && form.name() != null && !form.name().isBlank() ? form.name() : "(ohne Namen)";
        formFieldNames.add(name.toLowerCase(Locale.ROOT));
        VariableOccurrence occurrence = new VariableOccurrence(document, area, VariableKind.FORM_FIELD, keyword, name);
        String dataType = switch (keyword) {
            case "FORMCHECKBOX" -> DataTypes.BOOLEAN;
            case "FORMDROPDOWN" -> DataTypes.CHOICE;
            default -> null;
        };
        if (form != null) {
            occurrence.format(form.format()).options(form.options()).value(form.value());
            if (dataType == null) {
                dataType = form.dataType();
            }
        }
        return occurrence.dataType(dataType);
    }

    private static void applyFormatSwitches(FieldCode code, VariableOccurrence occurrence) {
        String date = code.switches().get("\\@");
        String number = code.switches().get("\\#");
        if (date != null && !date.isEmpty()) {
            occurrence.dataType(DataTypes.DATE).format(date);
        } else if (number != null && !number.isEmpty()) {
            occurrence.dataType(DataTypes.NUMBER).format(number);
        } else {
            String text = code.switches().get("\\*");
            if (text != null && !text.isEmpty()) {
                String formats = List.of(text.split(",\\s*")).stream()
                        .filter(f -> !f.equalsIgnoreCase("MERGEFORMAT") && !f.equalsIgnoreCase("CHARFORMAT"))
                        .reduce((a, b) -> a + ", " + b).orElse(null);
                occurrence.format(formats);
            }
        }
    }

    private static String nullIfEmpty(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String shorten(String text) {
        String normalized = text.replaceAll("\\s+", " ").strip();
        return normalized.length() > CONTEXT_LENGTH ? normalized.substring(0, CONTEXT_LENGTH) + "..." : normalized;
    }
}
