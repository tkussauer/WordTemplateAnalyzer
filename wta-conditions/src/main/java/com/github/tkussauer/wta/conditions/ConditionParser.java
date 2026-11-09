package com.github.tkussauer.wta.conditions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Findet alle Bedingungsfelder in einem Feldbaum und zerlegt sie.
 * <p>
 * Aufbau in Word: {@code IF Ausdruck1 Operator Ausdruck2 "DannText" "SonstText"},
 * {@code COMPARE Ausdruck1 Operator Ausdruck2}, {@code SKIPIF ...}, {@code NEXTIF ...}.
 * Ausdruecke und Texte koennen verschachtelte Felder enthalten, auch weitere Bedingungen.
 */
final class ConditionParser {

    static final Set<String> CONDITION_KEYWORDS = Set.of("IF", "COMPARE", "SKIPIF", "NEXTIF");
    private static final Set<String> DATA_KEYWORDS = Set.of(
            "MERGEFIELD", "DOCVARIABLE", "DOCPROPERTY", "REF", "ASK", "FILLIN", "SET");
    private static final List<String> OPERATORS = List.of("<=", ">=", "<>", "=", "<", ">");

    /** Ein Operand bzw. Text: Rohtext mit Markierungen fuer verschachtelte Felder. */
    private record Part(String raw, boolean quoted) {
    }

    private final String document;
    private final List<Condition> conditions = new ArrayList<>();
    private final Map<String, Integer> childCounters = new HashMap<>();

    private ConditionParser(String document) {
        this.document = document;
    }

    static List<Condition> parse(String document, List<FieldNode> roots) {
        ConditionParser parser = new ConditionParser(document);
        for (FieldNode root : roots) {
            parser.visit(root, null, 0, null);
        }
        return parser.conditions;
    }

    /**
     * @param parentId  Nummer der naechsten uebergeordneten Bedingung oder {@code null}
     * @param position  Lage in der uebergeordneten Bedingung
     */
    private void visit(FieldNode node, String parentId, int parentLevel, String position) {
        String keyword = node.keyword();
        if (!CONDITION_KEYWORDS.contains(keyword)) {
            for (FieldNode child : node.children()) {
                visit(child, parentId, parentLevel, position);
            }
            return;
        }
        String counterKey = parentId == null ? "" : parentId;
        int number = childCounters.merge(counterKey, 1, Integer::sum);
        String id = parentId == null ? String.valueOf(number) : parentId + "." + number;
        conditions.add(analyze(node, keyword, id, parentLevel + 1, position));

        // Verschachtelte Felder mit ihrer Lage in dieser Bedingung
        String marked = node.markedCode();
        List<FieldNode> children = node.children();
        for (int i = 0; i < children.size(); i++) {
            visit(children.get(i), id, parentLevel + 1, locate(marked, i, keyword) + " von " + id);
        }
    }

    private Condition analyze(FieldNode node, String keyword, String id, int level, String position) {
        List<FieldNode> children = node.children();
        Scanner scanner = new Scanner(node.markedCode());
        scanner.skipKeyword();
        Part left = scanner.operand();
        String operator = scanner.operator();
        Part right = scanner.operand();
        Part thenPart = "IF".equals(keyword) ? scanner.operand() : null;
        Part elsePart = "IF".equals(keyword) ? scanner.operand() : null;

        List<String> notes = new ArrayList<>();
        if (left == null || operator == null || right == null) {
            notes.add("Bedingung konnte nicht vollständig zerlegt werden – Feldcode prüfen");
        }
        String leftText = display(left, children, id);
        String rightText = display(right, children, id);
        String thenText = display(thenPart, children, id);
        String elseText = display(elsePart, children, id);

        Set<String> conditionFields = new LinkedHashSet<>();
        collectFields(left, children, conditionFields);
        collectFields(right, children, conditionFields);
        Set<String> textFields = new LinkedHashSet<>();
        collectFields(thenPart, children, textFields);
        collectFields(elsePart, children, textFields);

        if (conditionFields.isEmpty() && left != null) {
            notes.add("Bedingung enthält kein Datenfeld (konstantes Ergebnis oder Textmarke)");
        }
        boolean wildcard = right != null && right.quoted() && ("=".equals(operator) || "<>".equals(operator))
                && (right.raw().contains("*") || right.raw().contains("?"));
        if (wildcard) {
            notes.add("Platzhaltervergleich (* oder ?) – in Inspire als Muster/Regex umsetzen");
        }
        if (containsCondition(thenPart, children) || containsCondition(elsePart, children)) {
            notes.add("Enthält verschachtelte Bedingungen");
        }
        if (node.area().endsWith(" (nicht angezeigt)")) {
            notes.add("Steht in einer Kopf-/Fußzeile, die Word nicht anzeigt");
        }

        String compareValue = right != null && right.raw().indexOf(FieldNode.MARK_START) < 0 ? right.raw() : null;
        String readable = readable(keyword, leftText, operator, right, rightText, thenText, elseText);
        String expression = expression(left, operator, right, children);
        return new Condition(document, node.area(), id, level, position, keyword, leftText, operator, rightText,
                thenText, elseText, readable, expression, List.copyOf(conditionFields), List.copyOf(textFields),
                compareValue, notes, node.fullCode(), node.result(), node.context());
    }

    /** Lage des i-ten verschachtelten Feldes: Bedingung, Dann- oder Sonst-Zweig. */
    private static String locate(String marked, int childIndex, String keyword) {
        Scanner scanner = new Scanner(marked);
        scanner.skipKeyword();
        String marker = String.valueOf(FieldNode.MARK_START) + childIndex + FieldNode.MARK_END;
        Part left = scanner.operand();
        scanner.operator();
        Part right = scanner.operand();
        if (contains(left, marker) || contains(right, marker) || !"IF".equals(keyword)) {
            return "Bedingung";
        }
        Part thenPart = scanner.operand();
        if (contains(thenPart, marker)) {
            return "Dann-Zweig";
        }
        Part elsePart = scanner.operand();
        return contains(elsePart, marker) ? "Sonst-Zweig" : "Bedingung";
    }

    private static boolean contains(Part part, String marker) {
        return part != null && part.raw().contains(marker);
    }

    private static String readable(String keyword, String left, String operator, Part right, String rightText,
                                   String thenText, String elseText) {
        String condition = orMissing(left) + " " + describe(operator, right, orMissing(rightText));
        return switch (keyword) {
            case "IF" -> "WENN " + condition + " DANN " + quote(thenText) + " SONST " + quote(elseText);
            case "COMPARE" -> "VERGLEICH: " + condition + " (Ergebnis 1 oder 0)";
            case "SKIPIF" -> "Datensatz ÜBERSPRINGEN, WENN " + condition;
            case "NEXTIF" -> "Zum NÄCHSTEN Datensatz, WENN " + condition;
            default -> condition;
        };
    }

    private static String orMissing(String text) {
        return text == null ? "(fehlt)" : text;
    }

    private static String describe(String operator, Part right, String rightText) {
        if (operator == null) {
            return rightText;
        }
        boolean empty = right != null && right.quoted() && right.raw().isEmpty();
        return switch (operator) {
            case "=" -> empty ? "ist leer" : "ist gleich " + rightText;
            case "<>" -> empty ? "ist nicht leer" : "ist ungleich " + rightText;
            case "<" -> "ist kleiner als " + rightText;
            case ">" -> "ist größer als " + rightText;
            case "<=" -> "ist kleiner oder gleich " + rightText;
            case ">=" -> "ist größer oder gleich " + rightText;
            default -> operator + " " + rightText;
        };
    }

    /** Dann-/Sonst-Text fuer den Klartext; Texte sind bereits durch {@link #display} in „…“ gesetzt. */
    private static String quote(String text) {
        return text == null || text.isEmpty() || "„“".equals(text) ? "(nichts)" : text;
    }

    /** Normalisierter Ausdruck: Felder ueber ihren Namen, Texte in Anfuehrungszeichen, == und !=. */
    private static String expression(Part left, String operator, Part right, List<FieldNode> children) {
        if (left == null || operator == null || right == null) {
            return null;
        }
        String op = switch (operator) {
            case "=" -> "==";
            case "<>" -> "!=";
            default -> operator;
        };
        return term(left, children) + " " + op + " " + term(right, children);
    }

    private static String term(Part part, List<FieldNode> children) {
        String raw = part.raw();
        FieldNode single = singleField(raw, children);
        if (single != null && DATA_KEYWORDS.contains(single.keyword())) {
            return single.argument();
        }
        String text = expand(raw, children, null, false);
        if (!part.quoted() && text.matches("-?\\d+([.,]\\d+)?")) {
            return text;
        }
        return "\"" + text.replace("\"", "\\\"") + "\"";
    }

    /** Lesbare Darstellung eines Operanden bzw. Textes. */
    private static String display(Part part, List<FieldNode> children, String id) {
        if (part == null) {
            return null;
        }
        String text = expand(part.raw(), children, id, true);
        return part.quoted() && singleField(part.raw(), children) == null ? "„" + text + "“" : text;
    }

    /** Ersetzt Markierungen: Datenfelder als «Name», Bedingungen als [Bedingung n.m], sonst {CODE}. */
    private static String expand(String raw, List<FieldNode> children, String id, boolean readable) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c != FieldNode.MARK_START) {
                text.append(c);
                continue;
            }
            int end = raw.indexOf(FieldNode.MARK_END, i);
            int index = Integer.parseInt(raw.substring(i + 1, end));
            FieldNode child = children.get(index);
            String keyword = child.keyword();
            if ("MERGEFIELD".equals(keyword)) {
                text.append('«').append(child.argument()).append('»');
            } else if (DATA_KEYWORDS.contains(keyword)) {
                text.append('{').append(keyword).append(' ').append(child.argument()).append('}');
            } else if (CONDITION_KEYWORDS.contains(keyword) && readable && id != null) {
                // Nummer der verschachtelten Bedingung = Reihenfolge der Bedingungen unter den Kindern
                int number = 0;
                for (int k = 0; k <= index; k++) {
                    if (CONDITION_KEYWORDS.contains(children.get(k).keyword())) {
                        number++;
                    }
                }
                text.append("[Bedingung ").append(id).append('.').append(number).append(']');
            } else {
                text.append('{').append(child.fullCode()).append('}');
            }
            i = end;
        }
        return text.toString();
    }

    private static FieldNode singleField(String raw, List<FieldNode> children) {
        String trimmed = raw.strip();
        if (trimmed.length() >= 3 && trimmed.charAt(0) == FieldNode.MARK_START
                && trimmed.indexOf(FieldNode.MARK_END) == trimmed.length() - 1) {
            return children.get(Integer.parseInt(trimmed.substring(1, trimmed.length() - 1)));
        }
        return null;
    }

    private static void collectFields(Part part, List<FieldNode> children, Set<String> fields) {
        if (part == null) {
            return;
        }
        String raw = part.raw();
        for (int i = raw.indexOf(FieldNode.MARK_START); i >= 0; i = raw.indexOf(FieldNode.MARK_START, i + 1)) {
            int end = raw.indexOf(FieldNode.MARK_END, i);
            FieldNode child = children.get(Integer.parseInt(raw.substring(i + 1, end)));
            if (DATA_KEYWORDS.contains(child.keyword()) && !child.argument().isEmpty()) {
                fields.add(child.keyword() + " " + child.argument());
            }
        }
    }

    private static boolean containsCondition(Part part, List<FieldNode> children) {
        if (part == null) {
            return false;
        }
        String raw = part.raw();
        for (int i = raw.indexOf(FieldNode.MARK_START); i >= 0; i = raw.indexOf(FieldNode.MARK_START, i + 1)) {
            int end = raw.indexOf(FieldNode.MARK_END, i);
            if (CONDITION_KEYWORDS.contains(children.get(Integer.parseInt(raw.substring(i + 1, end))).keyword())) {
                return true;
            }
        }
        return false;
    }

    /** Zerlegt den markierten Feldcode in Operanden, Operator und Texte. */
    private static final class Scanner {
        private final String text;
        private int pos;

        Scanner(String text) {
            this.text = text;
        }

        void skipKeyword() {
            skipWhitespace();
            while (pos < text.length() && Character.isLetter(text.charAt(pos))) {
                pos++;
            }
        }

        /** Operand oder Text: in Anfuehrungszeichen, ein verschachteltes Feld oder ein Wort. */
        Part operand() {
            skipWhitespace();
            if (pos >= text.length() || text.charAt(pos) == '\\') {
                return null;
            }
            if (text.charAt(pos) == '"') {
                StringBuilder value = new StringBuilder();
                pos++;
                while (pos < text.length() && text.charAt(pos) != '"') {
                    if (text.charAt(pos) == '\\' && pos + 1 < text.length() && text.charAt(pos + 1) == '"') {
                        pos++;
                    }
                    value.append(text.charAt(pos++));
                }
                pos++;
                return new Part(value.toString(), true);
            }
            int start = pos;
            while (pos < text.length() && !Character.isWhitespace(text.charAt(pos))
                    && "=<>".indexOf(text.charAt(pos)) < 0) {
                if (text.charAt(pos) == FieldNode.MARK_START) {
                    pos = text.indexOf(FieldNode.MARK_END, pos) + 1;
                } else {
                    pos++;
                }
            }
            return pos > start ? new Part(text.substring(start, pos), false) : null;
        }

        String operator() {
            skipWhitespace();
            for (String operator : OPERATORS) {
                if (text.startsWith(operator, pos)) {
                    pos += operator.length();
                    return operator;
                }
            }
            return null;
        }

        private void skipWhitespace() {
            while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
                pos++;
            }
        }
    }
}
