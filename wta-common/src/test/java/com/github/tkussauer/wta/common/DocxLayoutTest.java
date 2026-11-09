package com.github.tkussauer.wta.common;

import org.apache.poi.wp.usermodel.HeaderFooterType;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.junit.jupiter.api.Test;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STSectionMark;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocxLayoutTest {

    @Test
    void firstPageHeaderIsOnlyDisplayedWithTitlePage() throws Exception {
        try (XWPFDocument doc = new XWPFDocument()) {
            doc.createParagraph().createRun().setText("Text");
            XWPFHeader standard = doc.createHeader(HeaderFooterType.DEFAULT);
            XWPFHeader first = doc.createHeader(HeaderFooterType.FIRST);
            CTSectPr sectPr = doc.getDocument().getBody().getSectPr();

            // "Erste Seite anders" deaktiviert: die Variante "first" zeigt Word nicht an
            while (sectPr.isSetTitlePg()) {
                sectPr.unsetTitlePg();
            }
            DocxLayout layout = DocxLayout.of(doc);
            assertTrue(layout.isDisplayed(doc, standard));
            assertFalse(layout.isDisplayed(doc, first));

            sectPr.addNewTitlePg();
            assertTrue(DocxLayout.of(doc).isDisplayed(doc, first));
        }
    }

    @Test
    void countsSectionsStartingOnNewPage() throws Exception {
        try (XWPFDocument doc = new XWPFDocument()) {
            // Abschnitt 1 (Beginn irrelevant), Abschnitt 2 ohne Typ = neue Seite, Abschnitt 3 fortlaufend
            doc.createParagraph().getCTP().addNewPPr().addNewSectPr().addNewType().setVal(STSectionMark.CONTINUOUS);
            doc.createParagraph().getCTP().addNewPPr().addNewSectPr();
            doc.getDocument().getBody().addNewSectPr().addNewType().setVal(STSectionMark.CONTINUOUS);

            DocxLayout layout = DocxLayout.of(doc);

            assertEquals(3, layout.sections().size());
            assertEquals("nextPage", layout.sections().get(1).startType());
            assertEquals(1, layout.sectionPageBreaks());
        }
    }
}
