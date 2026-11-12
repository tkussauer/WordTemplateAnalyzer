package com.github.tkussauer.wta.blocks;

import com.github.tkussauer.wta.common.DocxLayout;
import com.github.tkussauer.wta.common.FieldCode;
import com.github.tkussauer.wta.common.WordEventListener;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;

/**
 * Sammelt die Absaetze einer Vorlage. Felder werden nicht mit ihrem (zufaelligen) Ergebnis, sondern
 * als Platzhalter uebernommen, damit gleiche Bloecke mit gleichen Feldern erkannt werden.
 * Nicht angezeigte Kopf-/Fusszeilen werden uebersprungen.
 */
final class ParagraphCollector implements WordEventListener {

    private static final Set<String> DATA_KEYWORDS = Set.of("MERGEFIELD", "DOCVARIABLE", "DOCPROPERTY", "REF");

    private static final class Field {
        final StringBuilder code = new StringBuilder();
        boolean inCode = true;
    }

    private final List<Paragraph> paragraphs = new ArrayList<>();
    private final Deque<StringBuilder> open = new ArrayDeque<>();
    private final Deque<Field> fields = new ArrayDeque<>();
    private String area = "Inhalt";
    private boolean skip;

    @Override
    public void area(String area) {
        flush();
        skip = area.endsWith(DocxLayout.NOT_DISPLAYED);
        this.area = switch (area) {
            case "Kopfzeile", "Fußzeile", "Kopf-/Fußzeile" -> area;
            default -> "Inhalt";
        };
    }

    @Override
    public void paragraphStart() {
        open.push(new StringBuilder());
    }

    @Override
    public void paragraphEnd() {
        StringBuilder text = open.poll();
        if (text == null || skip) {
            return;
        }
        // Symbolzeichen (privater Unicode-Bereich, z. B. Aufzaehlungszeichen aus der Schrift Symbol) speichern
        // doc und docx unterschiedlich; sie werden fuer den Vergleich entfernt
        String normalized = text.toString().replaceAll("[\\uE000-\\uF8FF]", " ").replaceAll("\\s+", " ").strip();
        if (!normalized.isEmpty()) {
            paragraphs.add(new Paragraph(paragraphs.size() + 1, area, normalized));
        }
    }

    @Override
    public void text(String text) {
        // Feldergebnisse nicht uebernehmen, das Feld steht als Platzhalter im Text
        if (fields.isEmpty()) {
            current().append(text);
        }
    }

    @Override
    public void beginField() {
        fields.push(new Field());
    }

    @Override
    public void fieldCode(String code) {
        Field field = fields.peek();
        if (field != null && field.inCode) {
            field.code.append(code);
        }
    }

    @Override
    public void separateField() {
        Field field = fields.peek();
        if (field != null) {
            field.inCode = false;
        }
    }

    @Override
    public void endField() {
        Field field = fields.poll();
        if (field == null) {
            return;
        }
        String code = field.code.toString().replaceAll("\\s+", " ").strip();
        Field parent = fields.peek();
        if (parent != null) {
            if (parent.inCode) {
                parent.code.append('{').append(code).append('}');
            }
        } else {
            current().append(placeholder(code));
        }
    }

    List<Paragraph> finish() {
        flush();
        return paragraphs;
    }

    /** Datenfelder als «Name», alle anderen Felder mit ihrem Code, z. B. {PAGE}. */
    static String placeholder(String code) {
        FieldCode parsed = FieldCode.parse(code);
        if (DATA_KEYWORDS.contains(parsed.keyword()) && !parsed.argument(0).isEmpty()) {
            return "«" + parsed.argument(0) + "»";
        }
        String withoutFormat = code.replaceAll("\\\\\\* ?MERGEFORMAT", "").strip();
        return "{" + withoutFormat + "}";
    }

    private StringBuilder current() {
        if (open.isEmpty()) {
            paragraphStart();
        }
        return open.peek();
    }

    private void flush() {
        fields.clear();
        while (!open.isEmpty()) {
            paragraphEnd();
        }
    }
}
