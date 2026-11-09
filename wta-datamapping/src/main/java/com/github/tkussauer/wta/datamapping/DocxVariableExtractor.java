package com.github.tkussauer.wta.datamapping;

import com.github.tkussauer.wta.common.DocxLayout;
import org.apache.poi.ooxml.POIXMLDocumentPart;
import org.apache.poi.ooxml.POIXMLProperties;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFEndnote;
import org.apache.poi.xwpf.usermodel.XWPFFooter;
import org.apache.poi.xwpf.usermodel.XWPFFootnote;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.apache.poi.xwpf.usermodel.XWPFSettings;
import org.apache.xmlbeans.XmlCursor;
import org.apache.xmlbeans.XmlCursor.TokenType;
import org.apache.xmlbeans.XmlObject;
import org.openxmlformats.schemas.officeDocument.x2006.customProperties.CTProperty;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Variablen aus Office-Open-XML-Dokumenten (docx, dotx, docm, dotm).
 * <p>
 * Das XML wird generisch (ohne typisierte Schemaklassen) gelesen, damit auch Elemente erkannt
 * werden, die in poi-ooxml-lite fehlen (z. B. Dropdown-Formularfelder, w14-Kontrollkaestchen).
 */
public class DocxVariableExtractor implements VariableExtractor {

    static final String W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    private static final String W14 = "http://schemas.microsoft.com/office/word/2010/wordml";
    private static final String W15 = "http://schemas.microsoft.com/office/word/2012/wordml";
    private static final String MC = "http://schemas.openxmlformats.org/markup-compatibility/2006";
    private static final String DECLARE = "declare namespace w='" + W + "' declare namespace w14='" + W14
            + "' declare namespace w15='" + W15 + "' ";

    private static final QName FALLBACK = new QName(MC, "Fallback");
    private static final QName W_VAL = new QName(W, "val");
    private static final QName W_NAME = new QName(W, "name");
    private static final QName W_INSTR = new QName(W, "instr");
    private static final QName W_FLD_CHAR_TYPE = new QName(W, "fldCharType");

    @Override
    public TemplateVariables extract(Path file, String documentName, List<Pattern> placeholders,
                                     boolean includeBookmarks) throws IOException {
        VariableCollector collector = new VariableCollector(documentName, placeholders, includeBookmarks);
        try (InputStream in = Files.newInputStream(file); XWPFDocument doc = new XWPFDocument(in)) {
            collector.area("Hauptdokument");
            walk(doc.getDocument().getBody(), collector);
            // Nicht angezeigte Kopf-/Fusszeilen werden ausgewertet, aber gekennzeichnet
            DocxLayout layout = DocxLayout.of(doc);
            for (XWPFHeader header : doc.getHeaderList()) {
                collector.area("Kopfzeile" + (layout.isDisplayed(doc, header) ? "" : DocxLayout.NOT_DISPLAYED));
                walk(header._getHdrFtr(), collector);
            }
            for (XWPFFooter footer : doc.getFooterList()) {
                collector.area("Fußzeile" + (layout.isDisplayed(doc, footer) ? "" : DocxLayout.NOT_DISPLAYED));
                walk(footer._getHdrFtr(), collector);
            }
            for (XWPFFootnote note : doc.getFootnotes()) {
                collector.area("Fußnote");
                walk(note.getCTFtnEdn(), collector);
            }
            for (XWPFEndnote note : doc.getEndnotes()) {
                collector.area("Endnote");
                walk(note.getCTFtnEdn(), collector);
            }
            readDocumentVariables(doc, collector);
            readCustomProperties(doc, collector);
        }
        return new TemplateVariables(documentName, VariableExtractor.extension(file), collector.finish());
    }

    private static void walk(XmlObject root, VariableCollector collector) {
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
                        // Alternativdarstellung (z. B. VML-Textfeld) wuerde doppelt gezaehlt
                        cursor.toEndToken();
                        continue;
                    }
                    open.push(name);
                    if (W.equals(name.getNamespaceURI())) {
                        onStart(name.getLocalPart(), cursor, collector);
                    }
                } else if (token.isEnd()) {
                    if (open.isEmpty()) {
                        break;
                    }
                    QName name = open.pop();
                    if (W.equals(name.getNamespaceURI())) {
                        switch (name.getLocalPart()) {
                            case "p" -> collector.paragraphEnd();
                            case "fldSimple" -> collector.endField();
                            default -> { }
                        }
                    }
                } else if (token.isText() && !open.isEmpty() && W.equals(open.peek().getNamespaceURI())) {
                    switch (open.peek().getLocalPart()) {
                        case "t" -> collector.text(cursor.getChars());
                        case "instrText" -> collector.fieldCode(cursor.getChars());
                        default -> { }
                    }
                }
            }
        }
    }

    private static void onStart(String local, XmlCursor cursor, VariableCollector collector) {
        switch (local) {
            case "p" -> collector.paragraphStart();
            case "tab" -> collector.text("\t");
            case "fldSimple" -> {
                collector.beginField(null);
                collector.fieldCode(attribute(cursor, W_INSTR));
                collector.separateField();
            }
            case "fldChar" -> {
                switch (attribute(cursor, W_FLD_CHAR_TYPE)) {
                    case "begin" -> collector.beginField(formField(cursor.getObject()));
                    case "separate" -> collector.separateField();
                    case "end" -> collector.endField();
                    default -> { }
                }
            }
            case "bookmarkStart" -> collector.bookmark(attribute(cursor, W_NAME));
            case "sdt" -> {
                VariableOccurrence control = contentControl(cursor.getObject(), collector);
                if (control != null) {
                    collector.contentControl(control);
                }
            }
            default -> { }
        }
    }

    private static FormFieldInfo formField(XmlObject fldChar) {
        XmlObject ffData = first(fldChar, "./w:ffData");
        if (ffData == null) {
            return null;
        }
        String name = val(first(ffData, "./w:name"));
        if (first(ffData, "./w:checkBox") != null) {
            XmlObject checked = first(ffData, "./w:checkBox/w:default");
            return new FormFieldInfo(name, DataTypes.BOOLEAN, null, null,
                    checked != null ? ("0".equals(val(checked)) ? "Nein" : "Ja") : null);
        }
        if (first(ffData, "./w:ddList") != null) {
            return new FormFieldInfo(name, DataTypes.CHOICE, null, values(ffData, "./w:ddList/w:listEntry"), null);
        }
        String type = val(first(ffData, "./w:textInput/w:type"));
        String dataType = switch (type == null ? "" : type) {
            case "number" -> DataTypes.NUMBER;
            case "date", "currentDate" -> DataTypes.DATE;
            case "calculated" -> "berechnet";
            default -> DataTypes.TEXT;
        };
        return new FormFieldInfo(name, dataType, val(first(ffData, "./w:textInput/w:format")), null,
                val(first(ffData, "./w:textInput/w:default")));
    }

    /** Liefert {@code null} fuer Inhaltssteuerelemente ohne Datenbezug (Deckblatt, Seitenzahl, Verzeichnis). */
    private static VariableOccurrence contentControl(XmlObject sdt, VariableCollector collector) {
        XmlObject pr = first(sdt, "./w:sdtPr");
        if (pr == null || first(pr, "./w:docPartObj") != null || first(pr, "./w:citation") != null
                || first(pr, "./w:bibliography") != null) {
            return null;
        }
        String tag = val(first(pr, "./w:tag"));
        String alias = val(first(pr, "./w:alias"));
        String name = tag != null && !tag.isBlank() ? tag : alias != null && !alias.isBlank() ? alias : "(ohne Titel)";

        String type;
        String dataType;
        String options = null;
        String format = null;
        if (first(pr, "./w:date") != null) {
            type = "Datum";
            dataType = DataTypes.DATE;
            format = val(first(pr, "./w:date/w:dateFormat"));
        } else if (first(pr, "./w:dropDownList") != null) {
            type = "Dropdown-Liste";
            dataType = DataTypes.CHOICE;
            options = listItems(pr, "./w:dropDownList/w:listItem");
        } else if (first(pr, "./w:comboBox") != null) {
            type = "Kombinationsfeld";
            dataType = DataTypes.CHOICE;
            options = listItems(pr, "./w:comboBox/w:listItem");
        } else if (first(pr, "./w14:checkbox") != null) {
            type = "Kontrollkästchen";
            dataType = DataTypes.BOOLEAN;
        } else if (first(pr, "./w:picture") != null) {
            type = "Bild";
            dataType = DataTypes.IMAGE;
        } else if (first(pr, "./w15:repeatingSection") != null) {
            type = "Wiederholungsabschnitt";
            dataType = "Liste (wiederholend)";
        } else if (first(pr, "./w:text") != null) {
            type = "Nur-Text";
            dataType = DataTypes.guess(name);
        } else if (first(pr, "./w:group") != null) {
            type = "Gruppe";
            dataType = "Gruppe";
        } else {
            type = "Rich-Text";
            dataType = DataTypes.guess(name);
        }
        XmlObject binding = first(pr, "./w:dataBinding");
        String xpath = binding != null ? attribute(binding, "xpath") : null;
        String title = alias != null && !alias.equals(name) ? "Titel: " + alias : null;
        return new VariableOccurrence(collector.document(), collector.currentArea(), VariableKind.CONTENT_CONTROL,
                type, name).dataType(dataType).format(format).options(options).binding(xpath).fieldCode(title);
    }

    /**
     * Liest w:docVar aus word/settings.xml per StAX: Die Schemaklasse fuer w:docVars fehlt in
     * poi-ooxml-lite, ein Zugriff ueber XmlBeans scheitert daher.
     */
    private static void readDocumentVariables(XWPFDocument doc, VariableCollector collector) throws IOException {
        for (POIXMLDocumentPart part : doc.getRelations()) {
            if (!(part instanceof XWPFSettings)) {
                continue;
            }
            try (InputStream in = part.getPackagePart().getInputStream()) {
                XMLInputFactory factory = XMLInputFactory.newFactory();
                factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
                factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
                XMLStreamReader reader = factory.createXMLStreamReader(in);
                try {
                    while (reader.hasNext()) {
                        if (reader.next() == XMLStreamConstants.START_ELEMENT && W.equals(reader.getNamespaceURI())
                                && "docVar".equals(reader.getLocalName())) {
                            String name = reader.getAttributeValue(W, "name");
                            if (name != null && !name.isBlank()) {
                                collector.definition(VariableKind.DOC_VARIABLE, name,
                                        reader.getAttributeValue(W, "val"), "Dokumenteinstellungen");
                            }
                        }
                    }
                } finally {
                    reader.close();
                }
            } catch (XMLStreamException e) {
                throw new IOException("settings.xml nicht lesbar: " + e.getMessage(), e);
            }
        }
    }

    private static void readCustomProperties(XWPFDocument doc, VariableCollector collector) {
        POIXMLProperties properties = doc.getProperties();
        if (properties == null || properties.getCustomProperties() == null
                || properties.getCustomProperties().getUnderlyingProperties() == null) {
            return;
        }
        for (CTProperty property : properties.getCustomProperties().getUnderlyingProperties().getPropertyList()) {
            if (property.getName() != null) {
                collector.definition(VariableKind.DOC_PROPERTY, property.getName(), value(property),
                        "Dokumenteigenschaften");
            }
        }
    }

    private static String value(CTProperty property) {
        if (property.isSetLpwstr()) {
            return property.getLpwstr();
        }
        if (property.isSetLpstr()) {
            return property.getLpstr();
        }
        if (property.isSetI4()) {
            return String.valueOf(property.getI4());
        }
        if (property.isSetR8()) {
            return String.valueOf(property.getR8());
        }
        if (property.isSetBool()) {
            return property.getBool() ? "Ja" : "Nein";
        }
        if (property.isSetFiletime() && property.getFiletime() != null) {
            return property.getFiletime().toInstant().toString();
        }
        return null;
    }

    private static XmlObject first(XmlObject parent, String path) {
        if (parent == null) {
            return null;
        }
        XmlObject[] found = parent.selectPath(DECLARE + path);
        return found.length > 0 ? found[0] : null;
    }

    private static String val(XmlObject element) {
        return element != null ? attribute(element, "val") : null;
    }

    private static String values(XmlObject parent, String path) {
        List<String> values = new ArrayList<>();
        for (XmlObject entry : parent.selectPath(DECLARE + path)) {
            String value = val(entry);
            if (value != null) {
                values.add(value);
            }
        }
        return values.isEmpty() ? null : String.join(" | ", values);
    }

    private static String listItems(XmlObject parent, String path) {
        List<String> values = new ArrayList<>();
        for (XmlObject item : parent.selectPath(DECLARE + path)) {
            String display = attribute(item, "displayText");
            String value = attribute(item, "value");
            if (display != null && value != null && !display.equals(value)) {
                values.add(display + " = " + value);
            } else if (display != null || value != null) {
                values.add(display != null ? display : value);
            }
        }
        return values.isEmpty() ? null : String.join(" | ", values);
    }

    private static String attribute(XmlObject element, String localName) {
        try (XmlCursor cursor = element.newCursor()) {
            return cursor.getAttributeText(new QName(W, localName));
        }
    }

    private static String attribute(XmlCursor cursor, QName name) {
        String value = cursor.getAttributeText(name);
        return value != null ? value : "";
    }
}
