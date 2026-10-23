package com.github.tkussauer.wta.fonts;

import org.apache.poi.ooxml.util.POIXMLUnits;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFFooter;
import org.apache.poi.xwpf.usermodel.XWPFFootnote;
import org.apache.poi.xwpf.usermodel.XWPFEndnote;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.apache.poi.xwpf.usermodel.XWPFTheme;
import org.apache.xmlbeans.XmlCursor;
import org.apache.xmlbeans.XmlObject;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTDocDefaults;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTP;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTR;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTString;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTStyle;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTStyles;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTText;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STStyleType;

import javax.xml.namespace.QName;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Schriftanalyse fuer Office-Open-XML-Dokumente (docx, dotx, docm, dotm).
 * <p>
 * Fuer jeden Textabschnitt wird der effektive Schriftstil aus Dokumentstandard,
 * Absatzformatvorlage, Zeichenformatvorlage und direkter Formatierung ermittelt.
 */
public class DocxFontExtractor implements FontExtractor {

    private static final String W_NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    private static final String MC_NS = "http://schemas.openxmlformats.org/markup-compatibility/2006";
    private static final String RUN_PATH = "declare namespace w='" + W_NS + "' .//w:r";
    private static final QName PARAGRAPH = new QName(W_NS, "p");
    private static final QName FALLBACK = new QName(MC_NS, "Fallback");

    @Override
    public DocumentFonts extract(Path file, String documentName) throws IOException {
        try (InputStream in = Files.newInputStream(file); XWPFDocument doc = new XWPFDocument(in)) {
            return new Analysis(doc).run(documentName);
        }
    }

    private static final class Analysis {

        private final XWPFDocument document;
        private final XWPFTheme theme;
        private final Map<String, CTStyle> stylesById = new LinkedHashMap<>();
        private final RunProperties defaults = new RunProperties();
        private final Set<String> usedStyleIds = new HashSet<>();
        private final UsageCollector usages = new UsageCollector();
        private String defaultParagraphStyleId;

        Analysis(XWPFDocument doc) {
            this.document = doc;
            this.theme = doc.getTheme();
            readStyles(doc);
        }

        DocumentFonts run(String documentName) {
            scan(document.getDocument().getBody(), "Hauptdokument");
            for (XWPFHeader header : document.getHeaderList()) {
                scan(header._getHdrFtr(), "Kopfzeile");
            }
            for (XWPFFooter footer : document.getFooterList()) {
                scan(footer._getHdrFtr(), "Fußzeile");
            }
            for (XWPFFootnote footnote : document.getFootnotes()) {
                scan(footnote.getCTFtnEdn(), "Fußnote");
            }
            for (XWPFEndnote endnote : document.getEndnotes()) {
                scan(endnote.getCTFtnEdn(), "Endnote");
            }
            return new DocumentFonts(documentName, usages.toUsages(), styleDefinitions());
        }

        private void readStyles(XWPFDocument doc) {
            if (doc.getStyles() == null || doc.getStyles().getCtStyles() == null) {
                return;
            }
            CTStyles ctStyles = doc.getStyles().getCtStyles();
            CTDocDefaults docDefaults = ctStyles.getDocDefaults();
            if (docDefaults != null && docDefaults.getRPrDefault() != null) {
                defaults.apply(docDefaults.getRPrDefault().getRPr());
            }
            for (CTStyle style : ctStyles.getStyleList()) {
                if (style.getStyleId() == null) {
                    continue;
                }
                stylesById.putIfAbsent(style.getStyleId(), style);
                if (defaultParagraphStyleId == null && style.getType() == STStyleType.PARAGRAPH
                        && style.isSetDefault() && POIXMLUnits.parseOnOff(style.xgetDefault())) {
                    defaultParagraphStyleId = style.getStyleId();
                }
            }
        }

        private void scan(XmlObject root, String area) {
            if (root == null) {
                return;
            }
            for (XmlObject object : root.selectPath(RUN_PATH)) {
                if (object instanceof CTR run) {
                    scanRun(run, area);
                }
            }
        }

        private void scanRun(CTR run, String area) {
            String text = text(run);
            if (text.isBlank()) {
                return;
            }
            CTP paragraph = null;
            try (XmlCursor cursor = run.newCursor()) {
                while (cursor.toParent()) {
                    QName name = cursor.getName();
                    if (FALLBACK.equals(name)) {
                        // Alternativdarstellung (z. B. VML-Textfeld) wuerde doppelt gezaehlt
                        return;
                    }
                    if (paragraph == null && PARAGRAPH.equals(name) && cursor.getObject() instanceof CTP p) {
                        paragraph = p;
                    }
                }
            }

            String paragraphStyleId = paragraphStyleId(paragraph);
            CTRPr rPr = run.getRPr();
            String characterStyleId = rPr != null ? lastVal(rPr.getRStyleList()) : null;
            if (paragraphStyleId != null) {
                usedStyleIds.add(paragraphStyleId);
            }
            if (characterStyleId != null) {
                usedStyleIds.add(characterStyleId);
            }

            RunProperties props = defaults.copy();
            applyStyleChain(paragraphStyleId, props, new HashSet<>());
            applyStyleChain(characterStyleId, props, new HashSet<>());
            props.apply(rPr);

            usages.add(area, styleName(paragraphStyleId),
                    characterStyleId != null ? styleName(characterStyleId) : null,
                    RunProperties.hasFontProperties(rPr), props.toFontStyle(theme), text);
        }

        private String paragraphStyleId(CTP paragraph) {
            if (paragraph != null) {
                CTPPr pPr = paragraph.getPPr();
                if (pPr != null && pPr.getPStyle() != null && pPr.getPStyle().getVal() != null) {
                    return pPr.getPStyle().getVal();
                }
            }
            return defaultParagraphStyleId;
        }

        /** Wendet die Formatvorlage samt ihrer Basisvorlagen an (Basis zuerst). */
        private void applyStyleChain(String styleId, RunProperties props, Set<String> visited) {
            if (styleId == null || !visited.add(styleId)) {
                return;
            }
            CTStyle style = stylesById.get(styleId);
            if (style == null) {
                return;
            }
            if (style.getBasedOn() != null) {
                applyStyleChain(style.getBasedOn().getVal(), props, visited);
            }
            props.apply(style.getRPr());
        }

        private List<StyleDefinition> styleDefinitions() {
            List<StyleDefinition> result = new ArrayList<>();
            for (CTStyle style : stylesById.values()) {
                String type;
                RunProperties props = defaults.copy();
                if (style.getType() == STStyleType.PARAGRAPH) {
                    type = "Absatz";
                } else if (style.getType() == STStyleType.CHARACTER) {
                    type = "Zeichen";
                    applyStyleChain(defaultParagraphStyleId, props, new HashSet<>());
                } else {
                    continue;
                }
                applyStyleChain(style.getStyleId(), props, new HashSet<>());
                String basedOn = style.getBasedOn() != null ? styleName(style.getBasedOn().getVal()) : null;
                result.add(new StyleDefinition(style.getStyleId(), styleName(style.getStyleId()), type, basedOn,
                        props.toFontStyle(theme), usedStyleIds.contains(style.getStyleId())));
            }
            return result;
        }

        private String styleName(String styleId) {
            if (styleId == null) {
                return "(ohne Formatvorlage)";
            }
            CTStyle style = stylesById.get(styleId);
            if (style != null && style.getName() != null && style.getName().getVal() != null) {
                return style.getName().getVal();
            }
            return styleId;
        }

        private static String text(CTR run) {
            StringBuilder text = new StringBuilder();
            for (CTText t : run.getTList()) {
                text.append(t.getStringValue());
            }
            return text.toString();
        }

        private static String lastVal(List<CTString> values) {
            return values.isEmpty() ? null : values.get(values.size() - 1).getVal();
        }
    }
}
