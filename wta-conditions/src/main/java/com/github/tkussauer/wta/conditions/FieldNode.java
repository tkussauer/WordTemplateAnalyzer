package com.github.tkussauer.wta.conditions;

import com.github.tkussauer.wta.common.FieldCode;

import java.util.ArrayList;
import java.util.List;

/**
 * Ein Word-Feld mit seinem Feldcode. Verschachtelte Felder bleiben als eigene Knoten im Code erhalten,
 * z. B. {@code IF {MERGEFIELD Anrede} = "Herr" ...}.
 */
final class FieldNode {

    /** Markierungen fuer verschachtelte Felder im gerenderten Code (Zeichen aus dem privaten Unicode-Bereich). */
    static final char MARK_START = '';
    static final char MARK_END = '';

    private final String area;
    /** Teile des Feldcodes: {@link StringBuilder} fuer Text, {@link FieldNode} fuer verschachtelte Felder. */
    private final List<Object> code = new ArrayList<>();
    private final StringBuilder result = new StringBuilder();
    private boolean inCode = true;
    private String context;

    FieldNode(String area) {
        this.area = area;
    }

    String area() {
        return area;
    }

    boolean inCode() {
        return inCode;
    }

    void endCode() {
        inCode = false;
    }

    void appendCode(String text) {
        if (!code.isEmpty() && code.get(code.size() - 1) instanceof StringBuilder last) {
            last.append(text);
        } else {
            code.add(new StringBuilder(text));
        }
    }

    void addChild(FieldNode child) {
        code.add(child);
    }

    void appendResult(String text) {
        result.append(text);
    }

    /** Angezeigtes Feldergebnis, wie es zuletzt in der Vorlage gespeichert wurde. */
    String result() {
        return result.toString().strip();
    }

    String context() {
        return context;
    }

    void context(String context) {
        this.context = context;
    }

    /** Verschachtelte Felder in Reihenfolge. */
    List<FieldNode> children() {
        List<FieldNode> children = new ArrayList<>();
        for (Object part : code) {
            if (part instanceof FieldNode child) {
                children.add(child);
            }
        }
        return children;
    }

    /** Feldcode mit Markierungen {@code <index>} anstelle der verschachtelten Felder. */
    String markedCode() {
        StringBuilder text = new StringBuilder();
        int index = 0;
        for (Object part : code) {
            if (part instanceof FieldNode) {
                text.append(MARK_START).append(index++).append(MARK_END);
            } else {
                text.append(part);
            }
        }
        return text.toString().strip();
    }

    /** Vollstaendiger Feldcode mit verschachtelten Feldern in geschweiften Klammern. */
    String fullCode() {
        StringBuilder text = new StringBuilder();
        for (Object part : code) {
            if (part instanceof FieldNode child) {
                text.append("{ ").append(child.fullCode()).append(" }");
            } else {
                text.append(part);
            }
        }
        return text.toString().strip().replaceAll("\\s+", " ");
    }

    String keyword() {
        return FieldCode.parse(markedCode()).keyword();
    }

    /** Name eines Datenfeldes (MERGEFIELD Name) bzw. leer. */
    String argument() {
        return FieldCode.parse(markedCode()).argument(0);
    }
}
