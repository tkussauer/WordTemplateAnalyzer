package com.github.tkussauer.wta.complexity;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Zaehlt Feldfunktionen nach Kategorie und ermittelt ihre Verschachtelungstiefe.
 * Ein Feld besteht aus Beginn, Feldcode, optional Trenner und Ergebnis sowie Ende.
 */
final class FieldCounter {

    private static final Set<String> DATA = Set.of(
            "MERGEFIELD", "DOCVARIABLE", "DOCPROPERTY", "REF", "SET", "ASK", "FILLIN", "MERGEREC", "MERGESEQ");
    private static final Set<String> CONDITIONS = Set.of("IF", "COMPARE", "SKIPIF", "NEXTIF", "NEXT");
    private static final Set<String> INCLUDES = Set.of(
            "INCLUDETEXT", "INCLUDEPICTURE", "AUTOTEXT", "AUTOTEXTLIST", "LINK", "DATABASE");
    private static final Set<String> FORMS = Set.of("FORMTEXT", "FORMCHECKBOX", "FORMDROPDOWN");

    private static final Pattern KEYWORD = Pattern.compile("\\p{Alpha}+");
    /** Schluesselwort und Feldname, ggf. in Anfuehrungszeichen: MERGEFIELD "Geburts datum". */
    private static final Pattern DATA_FIELD = Pattern.compile("(\\p{Alpha}+)\\s+(?:\"([^\"]*)\"|(\\S+))");

    private static final class Frame {
        final StringBuilder code = new StringBuilder();
        boolean inCode = true;
    }

    private final TemplateMetrics metrics;
    private final Deque<Frame> open = new ArrayDeque<>();
    /** Unterschiedliche Datenfelder, z. B. "MERGEFIELD:name". */
    private final Set<String> dataFields = new HashSet<>();
    /** Je Bereich: Schluessel (z. B. "D|MERGEFIELD|name") -> Eintrag mit Anzahl. */
    private final Map<Region, Map<String, FieldEntry>> entries = new EnumMap<>(Region.class);
    private Region region = Region.CONTENT;
    private int maxDepth;

    FieldCounter(TemplateMetrics metrics) {
        this.metrics = metrics;
    }

    boolean hasOpenFields() {
        return !open.isEmpty();
    }

    /** Bereich der folgenden Felder. */
    void region(Region region) {
        this.region = region;
    }

    /** Beginn eines zusammengesetzten Feldes. */
    void begin() {
        open.push(new Frame());
        maxDepth = Math.max(maxDepth, open.size());
    }

    /** Text innerhalb des Feldcodes des innersten offenen Feldes. */
    void code(String text) {
        Frame frame = open.peek();
        if (frame != null && frame.inCode) {
            frame.code.append(text);
        }
    }

    /** Ab hier folgt das Feldergebnis. */
    void separate() {
        Frame frame = open.peek();
        if (frame != null && frame.inCode) {
            frame.inCode = false;
            count(frame.code.toString());
        }
    }

    void end() {
        Frame frame = open.poll();
        if (frame == null) {
            return;
        }
        if (frame.inCode) {
            count(frame.code.toString());
        }
        // Verschachteltes Feld im Code des aeusseren Feldes sichtbar halten, z. B. IF {MERGEFIELD X} = ...
        Frame parent = open.peek();
        if (parent != null && parent.inCode) {
            parent.code.append('{').append(frame.code.toString().strip()).append('}');
        }
    }

    /** Einfaches Feld mit vollstaendigem Feldcode (z. B. w:fldSimple). */
    void simple(String instruction) {
        maxDepth = Math.max(maxDepth, open.size() + 1);
        count(instruction);
        Frame parent = open.peek();
        if (parent != null && parent.inCode) {
            parent.code.append('{').append(instruction.strip()).append('}');
        }
    }

    /** Uebertraegt die maximale Verschachtelung; offene Felder werden verworfen. */
    void finish() {
        open.clear();
        metrics.set(Metric.FIELD_NESTING, Math.max(metrics.get(Metric.FIELD_NESTING), maxDepth));
        metrics.set(Metric.DATA_FIELDS, dataFields.size());
        Map<String, FieldEntry> content = entries.getOrDefault(Region.CONTENT, Map.of());
        Map<String, FieldEntry> headerFooter = entries.getOrDefault(Region.HEADER_FOOTER, Map.of());
        metrics.set(Metric.DATA_FIELDS_CONTENT, countEntries(content, false));
        metrics.set(Metric.DATA_FIELDS_HEADER_FOOTER, countEntries(headerFooter, false));
        metrics.set(Metric.CONDITIONS_CONTENT, occurrences(content, true));
        metrics.set(Metric.CONDITIONS_HEADER_FOOTER, occurrences(headerFooter, true));
        entries.values().forEach(byKey -> byKey.values().forEach(metrics::addFieldEntry));
        entries.clear();
    }

    private static int countEntries(Map<String, FieldEntry> entries, boolean condition) {
        return (int) entries.values().stream().filter(e -> e.condition() == condition).count();
    }

    private static int occurrences(Map<String, FieldEntry> entries, boolean condition) {
        return entries.values().stream().filter(e -> e.condition() == condition)
                .mapToInt(FieldEntry::occurrences).sum();
    }

    /** Zaehlt ein Vorkommen; gleiche Datenfelder bzw. gleiche Bedingungen werden zusammengefasst. */
    private void record(boolean condition, String keyword, String name) {
        String key = (condition ? "B|" : "D|") + keyword + "|" + (condition ? name : name.toLowerCase(Locale.ROOT));
        entries.computeIfAbsent(region, r -> new LinkedHashMap<>()).merge(key,
                new FieldEntry(region, condition, keyword, name, 1),
                (a, b) -> new FieldEntry(a.region(), a.condition(), a.keyword(), a.name(), a.occurrences() + 1));
    }

    private void count(String instruction) {
        String code = instruction.strip();
        if (code.isEmpty()) {
            return;
        }
        Metric category = classify(code);
        if (category == Metric.DATA_FIELDS) {
            // Datenfelder zaehlen je Name einmal; Wiederholungen nur als Verwendungen
            metrics.add(Metric.DATA_FIELD_USES, 1);
            Matcher matcher = DATA_FIELD.matcher(code);
            String keyword = matcher.lookingAt() ? matcher.group(1).toUpperCase(Locale.ROOT) : code;
            String name = matcher.lookingAt()
                    ? (matcher.group(2) != null ? matcher.group(2) : matcher.group(3)) : "";
            dataFields.add((keyword + ":" + name).toLowerCase(Locale.ROOT));
            record(false, keyword, name);
        } else {
            metrics.add(category, 1);
            if (category == Metric.CONDITIONS) {
                Matcher keyword = KEYWORD.matcher(code);
                record(true, keyword.lookingAt() ? keyword.group().toUpperCase(Locale.ROOT) : "IF",
                        code.replaceAll("\\s+", " "));
            }
        }
    }

    static Metric classify(String instruction) {
        // Schluesselwort sind die fuehrenden Buchstaben, z. B. auch bei "IF=" oder "IF{"
        Matcher matcher = KEYWORD.matcher(instruction.strip());
        String keyword = matcher.lookingAt() ? matcher.group().toUpperCase(Locale.ROOT) : "";
        if (DATA.contains(keyword)) {
            return Metric.DATA_FIELDS;
        }
        if (CONDITIONS.contains(keyword)) {
            return Metric.CONDITIONS;
        }
        if (INCLUDES.contains(keyword)) {
            return Metric.INCLUDES;
        }
        if (FORMS.contains(keyword)) {
            return Metric.FORM_FIELDS;
        }
        return Metric.OTHER_FIELDS;
    }
}
