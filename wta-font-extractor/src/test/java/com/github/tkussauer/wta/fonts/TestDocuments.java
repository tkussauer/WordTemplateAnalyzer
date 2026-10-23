package com.github.tkussauer.wta.fonts;

import org.apache.poi.wp.usermodel.HeaderFooterType;
import org.apache.poi.xwpf.usermodel.UnderlinePatterns;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTStyle;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTStyles;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STStyleType;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Erzeugt Word-Testdokumente mit definierten Formatvorlagen.
 */
final class TestDocuments {

    private TestDocuments() {
    }

    /**
     * Dokument mit Standard (Calibri 11), Ueberschrift (Arial 16 fett, basiert auf Standard),
     * Zeichenformat "Betont" (kursiv), direkter Formatierung und Kopfzeile.
     */
    static void writeSample(Path file) throws IOException {
        try (XWPFDocument doc = new XWPFDocument()) {
            CTStyles styles = CTStyles.Factory.newInstance();
            CTRPr defaults = styles.addNewDocDefaults().addNewRPrDefault().addNewRPr();
            defaults.addNewRFonts().setAscii("Calibri");
            defaults.addNewSz().setVal(BigInteger.valueOf(22));

            CTStyle normal = styles.addNewStyle();
            normal.setType(STStyleType.PARAGRAPH);
            normal.setStyleId("Standard");
            normal.setDefault(true);
            normal.addNewName().setVal("Normal");

            CTStyle heading = styles.addNewStyle();
            heading.setType(STStyleType.PARAGRAPH);
            heading.setStyleId("Ueberschrift1");
            heading.addNewName().setVal("heading 1");
            heading.addNewBasedOn().setVal("Standard");
            CTRPr headingRPr = heading.addNewRPr();
            headingRPr.addNewRFonts().setAscii("Arial");
            headingRPr.addNewB();
            headingRPr.addNewSz().setVal(BigInteger.valueOf(32));
            headingRPr.addNewColor().setVal("1F3864");

            CTStyle emphasis = styles.addNewStyle();
            emphasis.setType(STStyleType.CHARACTER);
            emphasis.setStyleId("Betont");
            emphasis.addNewName().setVal("Betont");
            emphasis.addNewRPr().addNewI();

            CTStyle unused = styles.addNewStyle();
            unused.setType(STStyleType.PARAGRAPH);
            unused.setStyleId("Fusszeile");
            unused.addNewName().setVal("footer");
            unused.addNewRPr().addNewSz().setVal(BigInteger.valueOf(16));

            doc.createStyles().setStyles(styles);

            XWPFParagraph title = doc.createParagraph();
            title.setStyle("Ueberschrift1");
            title.createRun().setText("Anschreiben");

            XWPFParagraph body = doc.createParagraph();
            body.createRun().setText("Sehr geehrte Damen und Herren, ");
            XWPFRun emphasized = body.createRun();
            emphasized.setStyle("Betont");
            emphasized.setText("wichtig");
            XWPFRun direct = body.createRun();
            direct.setFontFamily("Courier New");
            direct.setFontSize(9);
            direct.setUnderline(UnderlinePatterns.SINGLE);
            direct.setColor("FF0000");
            direct.setText("Kleingedrucktes");
            // Leere Runs werden ignoriert
            body.createRun().setText("   ");

            XWPFParagraph header = doc.createHeader(HeaderFooterType.DEFAULT).createParagraph();
            header.createRun().setText("Firma GmbH");

            try (OutputStream out = Files.newOutputStream(file)) {
                doc.write(out);
            }
        }
    }
}
