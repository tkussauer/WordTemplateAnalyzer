package com.github.tkussauer.wta.conditions;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Baut aus einem Strom von Ereignissen (Text, Feldbeginn, Feldcode, Trenner, Feldende, Absatzende)
 * den Baum der Felder auf. Die formatspezifischen Leser erzeugen diese Ereignisse.
 */
final class FieldTreeBuilder {

    private static final int CONTEXT_LENGTH = 160;

    private static final class Paragraph {
        final StringBuilder text = new StringBuilder();
        final List<FieldNode> fields = new ArrayList<>();
    }

    private final List<FieldNode> roots = new ArrayList<>();
    private final Deque<FieldNode> open = new ArrayDeque<>();
    private final Deque<Paragraph> paragraphs = new ArrayDeque<>();
    private String area = "Hauptdokument";

    void area(String area) {
        flush();
        this.area = area;
    }

    void paragraphStart() {
        paragraphs.push(new Paragraph());
    }

    void paragraphEnd() {
        Paragraph paragraph = paragraphs.poll();
        if (paragraph == null) {
            return;
        }
        String text = paragraph.text.toString().replaceAll("\\s+", " ").strip();
        if (text.length() > CONTEXT_LENGTH) {
            text = text.substring(0, CONTEXT_LENGTH) + "...";
        }
        for (FieldNode field : paragraph.fields) {
            if (field.context() == null) {
                field.context(text);
            }
        }
        Paragraph outer = paragraphs.peek();
        if (outer != null) {
            outer.text.append(' ').append(paragraph.text);
            outer.fields.addAll(paragraph.fields);
        }
    }

    /** Sichtbarer Text: gehoert zum Ergebnis des innersten Feldes bzw. zum Absatz. */
    void text(String text) {
        boolean inCode = open.stream().anyMatch(FieldNode::inCode);
        FieldNode top = open.peek();
        if (top != null && !top.inCode()) {
            top.appendResult(text);
        }
        if (!inCode) {
            currentParagraph().text.append(text);
        }
    }

    void beginField() {
        FieldNode node = new FieldNode(area);
        FieldNode parent = open.peek();
        if (parent != null && parent.inCode()) {
            parent.addChild(node);
        } else {
            // Felder im Ergebnis eines anderen Feldes oder auf oberster Ebene
            roots.add(node);
            currentParagraph().fields.add(node);
        }
        open.push(node);
    }

    void fieldCode(String text) {
        FieldNode top = open.peek();
        if (top != null && top.inCode()) {
            top.appendCode(text);
        }
    }

    void separateField() {
        FieldNode top = open.peek();
        if (top != null) {
            top.endCode();
        }
    }

    void endField() {
        FieldNode node = open.poll();
        if (node != null) {
            node.endCode();
        }
    }

    List<FieldNode> finish() {
        flush();
        return roots;
    }

    private void flush() {
        while (!open.isEmpty()) {
            endField();
        }
        while (!paragraphs.isEmpty()) {
            paragraphEnd();
        }
    }

    private Paragraph currentParagraph() {
        if (paragraphs.isEmpty()) {
            paragraphStart();
        }
        return paragraphs.peek();
    }
}
