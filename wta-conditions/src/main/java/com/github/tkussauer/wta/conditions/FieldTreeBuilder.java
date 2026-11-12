package com.github.tkussauer.wta.conditions;

import com.github.tkussauer.wta.common.WordEventListener;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Baut aus den Ereignissen des {@link com.github.tkussauer.wta.common.WordDocumentReader} den Baum der
 * Felder auf.
 */
final class FieldTreeBuilder implements WordEventListener {

    private static final int CONTEXT_LENGTH = 160;

    private static final class Paragraph {
        final StringBuilder text = new StringBuilder();
        final List<FieldNode> fields = new ArrayList<>();
    }

    private final List<FieldNode> roots = new ArrayList<>();
    private final Deque<FieldNode> open = new ArrayDeque<>();
    private final Deque<Paragraph> paragraphs = new ArrayDeque<>();
    private String area = "Hauptdokument";

    @Override
    public void area(String area) {
        flush();
        this.area = area;
    }

    @Override
    public void paragraphStart() {
        paragraphs.push(new Paragraph());
    }

    @Override
    public void paragraphEnd() {
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
    @Override
    public void text(String text) {
        boolean inCode = open.stream().anyMatch(FieldNode::inCode);
        FieldNode top = open.peek();
        if (top != null && !top.inCode()) {
            top.appendResult(text);
        }
        if (!inCode) {
            currentParagraph().text.append(text);
        }
    }

    @Override
    public void beginField() {
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

    @Override
    public void fieldCode(String text) {
        FieldNode top = open.peek();
        if (top != null && top.inCode()) {
            top.appendCode(text);
        }
    }

    @Override
    public void separateField() {
        FieldNode top = open.peek();
        if (top != null) {
            top.endCode();
        }
    }

    @Override
    public void endField() {
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
