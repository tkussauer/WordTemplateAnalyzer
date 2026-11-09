package com.github.tkussauer.wta.common;

import org.apache.poi.ooxml.POIXMLDocumentPart;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFSettings;
import org.apache.xmlbeans.XmlCursor;
import org.apache.xmlbeans.XmlObject;

import javax.xml.namespace.QName;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Abschnittslayout eines OOXML-Dokuments: wie die Abschnitte beginnen und welche Kopf- und
 * Fusszeilen Word tatsaechlich anzeigt.
 * <p>
 * Eine Kopf-/Fusszeile vom Typ "first" wird nur angezeigt, wenn der Abschnitt "Erste Seite anders"
 * aktiviert hat ({@code w:titlePg}), eine vom Typ "even" nur bei "Gerade/ungerade Seiten anders"
 * ({@code w:evenAndOddHeaders}). Vorlagen enthalten oft solche nicht angezeigten Varianten, z. B. als
 * Ueberbleibsel aus kopierten Abschnitten.
 */
public final class DocxLayout {

    /** Kennzeichnung fuer Bereiche aus nicht angezeigten Kopf-/Fusszeilen. */
    public static final String NOT_DISPLAYED = " (nicht angezeigt)";

    private static final String W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    private static final String R = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
    private static final QName W_VAL = new QName(W, "val");
    private static final QName W_TYPE = new QName(W, "type");
    private static final QName R_ID = new QName(R, "id");

    /**
     * Ein Abschnitt.
     *
     * @param startType  Abschnittsbeginn: nextPage (Standard), continuous, nextColumn, evenPage, oddPage
     * @param titlePage  "Erste Seite anders" ist aktiviert
     */
    public record Section(String startType, boolean titlePage) {

        /** {@code true}, wenn der Abschnitt auf einer neuen Seite beginnt. */
        public boolean startsNewPage() {
            return !"continuous".equals(startType) && !"nextColumn".equals(startType);
        }
    }

    private final List<Section> sections;
    private final Set<String> visibleRelationIds;
    private final boolean hasReferences;

    private DocxLayout(List<Section> sections, Set<String> visibleRelationIds, boolean hasReferences) {
        this.sections = List.copyOf(sections);
        this.visibleRelationIds = visibleRelationIds;
        this.hasReferences = hasReferences;
    }

    public static DocxLayout of(XWPFDocument doc) {
        boolean evenAndOdd = false;
        for (POIXMLDocumentPart part : doc.getRelations()) {
            if (part instanceof XWPFSettings settings) {
                evenAndOdd = settings.getEvenAndOddHeadings();
            }
        }
        List<Section> sections = new ArrayList<>();
        Set<String> visible = new HashSet<>();
        boolean hasReferences = false;
        XmlObject body = doc.getDocument().getBody();
        if (body != null) {
            for (XmlObject sectPr : body.selectPath("declare namespace w='" + W + "' .//w:sectPr")) {
                if (isTrackedChange(sectPr)) {
                    continue;
                }
                String type = "nextPage";
                boolean titlePage = false;
                List<String[]> references = new ArrayList<>();
                try (XmlCursor cursor = sectPr.newCursor()) {
                    if (cursor.toFirstChild()) {
                        do {
                            QName name = cursor.getName();
                            if (!W.equals(name.getNamespaceURI())) {
                                continue;
                            }
                            switch (name.getLocalPart()) {
                                case "type" -> type = valueOr(cursor.getAttributeText(W_VAL), type);
                                case "titlePg" -> titlePage = isOn(cursor.getAttributeText(W_VAL));
                                case "headerReference", "footerReference" -> references.add(new String[] {
                                        valueOr(cursor.getAttributeText(W_TYPE), "default"),
                                        cursor.getAttributeText(R_ID)});
                                default -> { }
                            }
                        } while (cursor.toNextSibling());
                    }
                }
                for (String[] reference : references) {
                    hasReferences = true;
                    boolean shown = switch (reference[0]) {
                        case "first" -> titlePage;
                        case "even" -> evenAndOdd;
                        default -> true;
                    };
                    if (shown && reference[1] != null) {
                        visible.add(reference[1]);
                    }
                }
                sections.add(new Section(type, titlePage));
            }
        }
        return new DocxLayout(sections, visible, hasReferences);
    }

    /** Abschnitte in Dokumentreihenfolge. */
    public List<Section> sections() {
        return sections;
    }

    /** Anzahl der Abschnittswechsel, die eine neue Seite beginnen (der erste Abschnitt zaehlt nicht). */
    public int sectionPageBreaks() {
        int breaks = 0;
        for (int i = 1; i < sections.size(); i++) {
            breaks += sections.get(i).startsNewPage() ? 1 : 0;
        }
        return breaks;
    }

    /**
     * @return {@code true}, wenn Word die Kopf-/Fusszeile {@code part} in einem Abschnitt anzeigt.
     *         Ohne jede Referenz in den Abschnitten (untypische Erzeuger) gilt alles als sichtbar.
     */
    public boolean isDisplayed(XWPFDocument doc, POIXMLDocumentPart part) {
        if (!hasReferences) {
            return true;
        }
        String id = doc.getRelationId(part);
        return id == null || visibleRelationIds.contains(id);
    }

    /** Abschnittseigenschaften einer nachverfolgten Aenderung beschreiben den alten Zustand. */
    private static boolean isTrackedChange(XmlObject sectPr) {
        try (XmlCursor cursor = sectPr.newCursor()) {
            return cursor.toParent() && "sectPrChange".equals(cursor.getName().getLocalPart());
        }
    }

    private static boolean isOn(String value) {
        return value == null || !(value.equals("0") || value.equalsIgnoreCase("false") || value.equalsIgnoreCase("off"));
    }

    private static String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
