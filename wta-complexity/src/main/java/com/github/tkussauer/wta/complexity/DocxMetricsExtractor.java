package com.github.tkussauer.wta.complexity;

import com.github.tkussauer.wta.fonts.DocxFontExtractor;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.openxml4j.opc.PackagePart;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFEndnote;
import org.apache.poi.xwpf.usermodel.XWPFFootnote;
import org.apache.poi.xwpf.usermodel.XWPFHeaderFooter;
import org.apache.xmlbeans.XmlCursor;
import org.apache.xmlbeans.XmlCursor.TokenType;
import org.apache.xmlbeans.XmlObject;

import javax.xml.namespace.QName;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Komplexitaetskennzahlen fuer Office-Open-XML-Dokumente (docx, dotx, docm, dotm).
 */
public class DocxMetricsExtractor implements MetricsExtractor {

    private static final String W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    private static final String A = "http://schemas.openxmlformats.org/drawingml/2006/main";
    private static final String C = "http://schemas.openxmlformats.org/drawingml/2006/chart";
    private static final String V = "urn:schemas-microsoft-com:vml";
    private static final String MC = "http://schemas.openxmlformats.org/markup-compatibility/2006";

    private static final QName FALLBACK = new QName(MC, "Fallback");
    private static final QName W_VAL = new QName(W, "val");
    private static final QName W_TYPE = new QName(W, "type");
    private static final QName W_NAME = new QName(W, "name");
    private static final QName W_INSTR = new QName(W, "instr");
    private static final QName W_FLD_CHAR_TYPE = new QName(W, "fldCharType");

    private final DocxFontExtractor fontExtractor = new DocxFontExtractor();

    @Override
    public TemplateMetrics extract(Path file, String documentName) throws IOException {
        TemplateMetrics metrics = new TemplateMetrics(documentName, extension(file));
        try (InputStream in = Files.newInputStream(file); XWPFDocument doc = new XWPFDocument(in)) {
            Walker walker = new Walker(metrics);
            walker.walk(doc.getDocument().getBody());

            List<XWPFHeaderFooter> headersFooters = new ArrayList<>(doc.getHeaderList());
            headersFooters.addAll(doc.getFooterList());
            int withContent = 0;
            for (XWPFHeaderFooter part : headersFooters) {
                walker.walk(part._getHdrFtr());
                if (!part.getText().isBlank() || !part.getAllPictures().isEmpty()) {
                    withContent++;
                }
            }
            metrics.set(Metric.HEADERS_FOOTERS, withContent);

            int notes = 0;
            for (XWPFFootnote note : doc.getFootnotes()) {
                walker.walk(note.getCTFtnEdn());
                notes += note.getCTFtnEdn().isSetType() ? 0 : 1;
            }
            for (XWPFEndnote note : doc.getEndnotes()) {
                walker.walk(note.getCTFtnEdn());
                notes += note.getCTFtnEdn().isSetType() ? 0 : 1;
            }
            metrics.set(Metric.NOTES, notes);
            walker.finish();

            int pages = 0;
            if (doc.getProperties() != null && doc.getProperties().getExtendedProperties() != null) {
                pages = doc.getProperties().getExtendedProperties().getPages();
            }
            metrics.set(Metric.PAGES, Math.max(pages, walker.pageBreaks + 1));
            metrics.set(Metric.MACROS, hasMacros(doc) ? 1 : 0);
        }
        FontMetrics.apply(fontExtractor.extract(file, documentName), metrics);
        return metrics;
    }

    private static boolean hasMacros(XWPFDocument doc) {
        try {
            for (PackagePart part : doc.getPackage().getParts()) {
                if (part.getPartName().getName().toLowerCase(Locale.ROOT).endsWith("vbaproject.bin")) {
                    return true;
                }
            }
        } catch (InvalidFormatException e) {
            return false;
        }
        return false;
    }

    static String extension(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
    }

    /**
     * Durchlaeuft das XML eines Dokumentteils in Dokumentreihenfolge.
     */
    private static final class Walker {

        private final TemplateMetrics metrics;
        private final FieldCounter fields;
        private final Set<String> listIds = new HashSet<>();
        private int pageBreaks;

        Walker(TemplateMetrics metrics) {
            this.metrics = metrics;
            this.fields = new FieldCounter(metrics);
        }

        void walk(XmlObject root) {
            if (root == null) {
                return;
            }
            Deque<QName> open = new ArrayDeque<>();
            int tableDepth = 0;
            int objectDepth = 0;
            try (XmlCursor cursor = root.newCursor()) {
                while (true) {
                    TokenType token = cursor.toNextToken();
                    if (token.isNone() || token.isEnddoc()) {
                        break;
                    }
                    if (token.isStart()) {
                        QName name = cursor.getName();
                        if (FALLBACK.equals(name)) {
                            // Alternativdarstellung wuerde doppelt gezaehlt
                            cursor.toEndToken();
                            continue;
                        }
                        open.push(name);
                        String ns = name.getNamespaceURI();
                        String local = name.getLocalPart();
                        if (W.equals(ns)) {
                            switch (local) {
                                case "tbl" -> {
                                    metrics.add(Metric.TABLES, 1);
                                    if (tableDepth > 0) {
                                        metrics.add(Metric.NESTED_TABLES, 1);
                                    }
                                    tableDepth++;
                                }
                                case "object" -> {
                                    metrics.add(Metric.EMBEDDED_OBJECTS, 1);
                                    objectDepth++;
                                }
                                default -> onWordElement(local, cursor);
                            }
                        } else if (A.equals(ns) && "blip".equals(local)
                                || C.equals(ns) && "chart".equals(local)
                                || V.equals(ns) && "imagedata".equals(local) && objectDepth == 0) {
                            metrics.add(Metric.IMAGES, 1);
                        }
                    } else if (token.isEnd()) {
                        if (open.isEmpty()) {
                            break;
                        }
                        QName name = open.pop();
                        if (W.equals(name.getNamespaceURI())) {
                            if ("tbl".equals(name.getLocalPart())) {
                                tableDepth--;
                            } else if ("object".equals(name.getLocalPart())) {
                                objectDepth--;
                            }
                        }
                    } else if (token.isText() && !open.isEmpty()
                            && W.equals(open.peek().getNamespaceURI()) && "instrText".equals(open.peek().getLocalPart())) {
                        fields.code(cursor.getChars());
                    }
                }
            }
        }

        private void onWordElement(String local, XmlCursor cursor) {
            switch (local) {
                case "p" -> metrics.add(Metric.PARAGRAPHS, 1);
                case "sectPr" -> metrics.add(Metric.SECTIONS, 1);
                case "txbxContent" -> metrics.add(Metric.TEXT_BOXES, 1);
                case "sdt" -> metrics.add(Metric.CONTENT_CONTROLS, 1);
                case "fldSimple" -> fields.simple(attribute(cursor, W_INSTR));
                case "fldChar" -> {
                    String type = attribute(cursor, W_FLD_CHAR_TYPE);
                    switch (type) {
                        case "begin" -> fields.begin();
                        case "separate" -> fields.separate();
                        case "end" -> fields.end();
                        default -> { }
                    }
                }
                case "numId" -> {
                    String id = attribute(cursor, W_VAL);
                    if (!id.isEmpty() && !"0".equals(id)) {
                        listIds.add(id);
                    }
                }
                case "bookmarkStart" -> {
                    String name = attribute(cursor, W_NAME);
                    if (!name.isEmpty() && !name.startsWith("_")) {
                        metrics.add(Metric.BOOKMARKS, 1);
                    }
                }
                case "br" -> {
                    if ("page".equals(attribute(cursor, W_TYPE))) {
                        pageBreaks++;
                    }
                }
                case "pageBreakBefore" -> {
                    String val = attribute(cursor, W_VAL);
                    if (val.isEmpty() || "true".equals(val) || "1".equals(val) || "on".equals(val)) {
                        pageBreaks++;
                    }
                }
                default -> { }
            }
        }

        void finish() {
            fields.finish();
            metrics.set(Metric.LISTS, listIds.size());
        }

        private static String attribute(XmlCursor cursor, QName name) {
            String value = cursor.getAttributeText(name);
            return value != null ? value : "";
        }
    }
}
