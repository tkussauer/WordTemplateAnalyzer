package com.github.tkussauer.wta.complexity;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
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

    private static final class Frame {
        final StringBuilder code = new StringBuilder();
        boolean inCode = true;
    }

    private final TemplateMetrics metrics;
    private final Deque<Frame> open = new ArrayDeque<>();
    private int maxDepth;

    FieldCounter(TemplateMetrics metrics) {
        this.metrics = metrics;
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
        if (frame != null && frame.inCode) {
            count(frame.code.toString());
        }
    }

    /** Einfaches Feld mit vollstaendigem Feldcode (z. B. w:fldSimple). */
    void simple(String instruction) {
        maxDepth = Math.max(maxDepth, open.size() + 1);
        count(instruction);
    }

    /** Uebertraegt die maximale Verschachtelung; offene Felder werden verworfen. */
    void finish() {
        open.clear();
        metrics.set(Metric.FIELD_NESTING, Math.max(metrics.get(Metric.FIELD_NESTING), maxDepth));
    }

    private void count(String instruction) {
        String code = instruction.strip();
        if (code.isEmpty()) {
            return;
        }
        metrics.add(classify(code), 1);
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
