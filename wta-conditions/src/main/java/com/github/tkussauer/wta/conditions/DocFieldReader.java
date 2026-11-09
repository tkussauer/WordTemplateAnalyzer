package com.github.tkussauer.wta.conditions;

import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.usermodel.Range;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;

/**
 * Felder aus binaeren Word-97-2003-Dokumenten (doc, dot). Felder sind dort im Text durch die
 * Steuerzeichen 0x13 (Beginn), 0x14 (Trenner) und 0x15 (Ende) markiert.
 */
class DocFieldReader implements FieldReader {

    private static final char FIELD_BEGIN = 0x13;
    private static final char FIELD_SEPARATOR = 0x14;
    private static final char FIELD_END = 0x15;
    private static final char PARAGRAPH_END = 0x0D;
    private static final char CELL_END = 0x07;
    private static final char LINE_BREAK = 0x0B;

    @Override
    public List<FieldNode> read(Path file) throws IOException {
        FieldTreeBuilder builder = new FieldTreeBuilder();
        try (InputStream in = Files.newInputStream(file); HWPFDocument doc = new HWPFDocument(in)) {
            scan(doc::getRange, "Hauptdokument", builder);
            scan(doc::getHeaderStoryRange, "Kopf-/Fußzeile", builder);
            scan(doc::getFootnoteRange, "Fußnote", builder);
            scan(doc::getEndnoteRange, "Endnote", builder);
            scan(doc::getMainTextboxRange, "Textfeld", builder);
        }
        return builder.finish();
    }

    private static void scan(Supplier<Range> rangeSupplier, String area, FieldTreeBuilder builder) {
        Range range;
        try {
            range = rangeSupplier.get();
        } catch (RuntimeException e) {
            // Bereich in der Datei nicht vorhanden
            return;
        }
        if (range == null) {
            return;
        }
        builder.area(area);
        String text = range.text();
        StringBuilder chunk = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case FIELD_BEGIN, FIELD_SEPARATOR, FIELD_END, PARAGRAPH_END, CELL_END -> {
                    flush(chunk, builder);
                    switch (c) {
                        case FIELD_BEGIN -> builder.beginField();
                        case FIELD_SEPARATOR -> builder.separateField();
                        case FIELD_END -> builder.endField();
                        default -> builder.paragraphEnd();
                    }
                }
                case LINE_BREAK, '\t' -> chunk.append(' ');
                default -> {
                    if (c >= 0x20) {
                        chunk.append(c);
                    }
                }
            }
        }
        flush(chunk, builder);
        builder.paragraphEnd();
    }

    /** Text gehoert je nach Zustand zum Feldcode oder zum sichtbaren Text. */
    private static void flush(StringBuilder chunk, FieldTreeBuilder builder) {
        if (!chunk.isEmpty()) {
            builder.fieldCode(chunk.toString());
            builder.text(chunk.toString());
            chunk.setLength(0);
        }
    }
}
