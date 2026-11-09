package com.github.tkussauer.wta.conditions;

import com.github.tkussauer.wta.common.DocxLayout;
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
import java.util.List;

/**
 * Felder aus Office-Open-XML-Dokumenten (docx, dotx, docm, dotm): Hauptdokument, Kopf-/Fusszeilen
 * (nicht angezeigte gekennzeichnet), Fuss- und Endnoten.
 */
class DocxFieldReader implements FieldReader {

    private static final String W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    private static final String MC = "http://schemas.openxmlformats.org/markup-compatibility/2006";
    private static final QName FALLBACK = new QName(MC, "Fallback");
    private static final QName W_INSTR = new QName(W, "instr");
    private static final QName W_FLD_CHAR_TYPE = new QName(W, "fldCharType");

    @Override
    public List<FieldNode> read(Path file) throws IOException {
        FieldTreeBuilder builder = new FieldTreeBuilder();
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
        return builder.finish();
    }

    private static void walk(XmlObject root, FieldTreeBuilder builder) {
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

    private static void onStart(String local, XmlCursor cursor, FieldTreeBuilder builder) {
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
}
