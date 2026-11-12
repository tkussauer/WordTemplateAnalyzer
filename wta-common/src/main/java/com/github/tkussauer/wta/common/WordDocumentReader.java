package com.github.tkussauer.wta.common;

import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.usermodel.Range;
import org.apache.poi.poifs.filesystem.FileMagic;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFEndnote;
import org.apache.poi.xwpf.usermodel.XWPFFooter;
import org.apache.poi.xwpf.usermodel.XWPFFootnote;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.apache.xmlbeans.XmlCursor;
import org.apache.xmlbeans.XmlCursor.TokenType;
import org.apache.xmlbeans.XmlObject;

import javax.xml.namespace.QName;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * Liest ein Word-Dokument (docx, dotx, docm, dotm oder doc, dot) und meldet Absaetze, Text und Felder
 * an einen {@link WordEventListener}. Das Format wird am Dateiinhalt erkannt, nicht an der Endung.
 * <p>
 * Bereiche: Hauptdokument, Kopf-/Fusszeilen (nicht angezeigte mit {@link DocxLayout#NOT_DISPLAYED}
 * gekennzeichnet), Fuss- und Endnoten; bei doc zusaetzlich Textfelder.
 */
public final class WordDocumentReader {

    private static final String W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    private static final String MC = "http://schemas.openxmlformats.org/markup-compatibility/2006";
    private static final QName FALLBACK = new QName(MC, "Fallback");
    private static final QName W_INSTR = new QName(W, "instr");
    private static final QName W_FLD_CHAR_TYPE = new QName(W, "fldCharType");

    private static final char FIELD_BEGIN = 0x13;
    private static final char FIELD_SEPARATOR = 0x14;
    private static final char FIELD_END = 0x15;
    private static final char PARAGRAPH_END = 0x0D;
    private static final char CELL_END = 0x07;
    private static final char LINE_BREAK = 0x0B;

    private WordDocumentReader() {
    }

    public static void read(Path file, WordEventListener listener) throws IOException {
        FileMagic magic = FileMagic.valueOf(file.toFile());
        switch (magic) {
            case OOXML -> readDocx(file, listener);
            case OLE2 -> readDoc(file, listener);
            default -> throw new IOException("Kein unterstuetztes Word-Format (erkannt: " + magic + ")");
        }
    }

    /** Dateiendung in Kleinbuchstaben, z. B. "docx". */
    public static String extension(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
    }

    private static void readDocx(Path file, WordEventListener builder) throws IOException {
        try (InputStream in = Files.newInputStream(file); XWPFDocument doc = new XWPFDocument(in)) {
            builder.area("Hauptdokument");
            walk(doc.getDocument().getBody(), builder);
            DocxLayout layout = DocxLayout.of(doc);
            for (XWPFHeader header : doc.getHeaderList()) {
                builder.area("Kopfzeile" + (layout.isDisplayed(doc, header) ? "" : DocxLayout.NOT_DISPLAYED));
                walk(header._getHdrFtr(), builder);
            }
            for (XWPFFooter footer : doc.getFooterList()) {
                builder.area("Fußzeile" + (layout.isDisplayed(doc, footer) ? "" : DocxLayout.NOT_DISPLAYED));
                walk(footer._getHdrFtr(), builder);
            }
            for (XWPFFootnote note : doc.getFootnotes()) {
                builder.area("Fußnote");
                walk(note.getCTFtnEdn(), builder);
            }
            for (XWPFEndnote note : doc.getEndnotes()) {
                builder.area("Endnote");
                walk(note.getCTFtnEdn(), builder);
            }
        }
    }

    private static void readDoc(Path file, WordEventListener builder) throws IOException {
        try (InputStream in = Files.newInputStream(file); HWPFDocument doc = new HWPFDocument(in)) {
            scan(doc::getRange, "Hauptdokument", builder);
            scan(doc::getHeaderStoryRange, "Kopf-/Fußzeile", builder);
            scan(doc::getFootnoteRange, "Fußnote", builder);
            scan(doc::getEndnoteRange, "Endnote", builder);
            scan(doc::getMainTextboxRange, "Textfeld", builder);
        }
    }

    private static void walk(XmlObject root, WordEventListener builder) {
        if (root == null) {
            return;
        }
        Deque<QName> open = new ArrayDeque<>();
        try (XmlCursor cursor = root.newCursor()) {
            while (true) {
                TokenType token = cursor.toNextToken();
                if (token.isNone() || token.isEnddoc()) {
                    break;
                }
                if (token.isStart()) {
                    QName name = cursor.getName();
                    if (FALLBACK.equals(name)) {
                        // Alternativdarstellung (z. B. VML-Textfeld) wuerde doppelt gelesen
                        cursor.toEndToken();
                        continue;
                    }
                    open.push(name);
                    if (W.equals(name.getNamespaceURI())) {
                        onStart(name.getLocalPart(), cursor, builder);
                    }
                } else if (token.isEnd()) {
                    if (open.isEmpty()) {
                        break;
                    }
                    QName name = open.pop();
                    if (W.equals(name.getNamespaceURI())) {
                        switch (name.getLocalPart()) {
                            case "p" -> builder.paragraphEnd();
                            case "fldSimple" -> builder.endField();
                            default -> { }
                        }
                    }
                } else if (token.isText() && !open.isEmpty() && W.equals(open.peek().getNamespaceURI())) {
                    switch (open.peek().getLocalPart()) {
                        case "t" -> builder.text(cursor.getChars());
                        case "instrText" -> builder.fieldCode(cursor.getChars());
                        default -> { }
                    }
                }
            }
        }
    }

    private static void onStart(String local, XmlCursor cursor, WordEventListener builder) {
        switch (local) {
            case "p" -> builder.paragraphStart();
            case "tab" -> builder.text("\t");
            case "fldSimple" -> {
                builder.beginField();
                String instr = cursor.getAttributeText(W_INSTR);
                builder.fieldCode(instr != null ? instr : "");
                builder.separateField();
            }
            case "fldChar" -> {
                String type = cursor.getAttributeText(W_FLD_CHAR_TYPE);
                if ("begin".equals(type)) {
                    builder.beginField();
                } else if ("separate".equals(type)) {
                    builder.separateField();
                } else if ("end".equals(type)) {
                    builder.endField();
                }
            }
            default -> { }
        }
    }

    private static void scan(Supplier<Range> rangeSupplier, String area, WordEventListener builder) {
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
    private static void flush(StringBuilder chunk, WordEventListener builder) {
        if (!chunk.isEmpty()) {
            builder.fieldCode(chunk.toString());
            builder.text(chunk.toString());
            chunk.setLength(0);
        }
    }
}
