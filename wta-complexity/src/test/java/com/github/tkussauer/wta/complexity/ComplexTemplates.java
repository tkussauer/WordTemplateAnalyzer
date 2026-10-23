package com.github.tkussauer.wta.complexity;

import org.apache.poi.wp.usermodel.HeaderFooterType;
import org.apache.poi.xwpf.usermodel.BreakType;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBookmark;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTP;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTR;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSimpleField;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STFldCharType;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Erzeugt Testvorlagen unterschiedlicher Komplexitaet.
 */
final class ComplexTemplates {

    private ComplexTemplates() {
    }

    /** Einseitiger Brief mit wenigen Seriendruckfeldern. */
    static void writeSimple(Path file) throws IOException {
        try (XWPFDocument doc = new XWPFDocument()) {
            XWPFParagraph address = doc.createParagraph();
            simpleField(address.getCTP(), " MERGEFIELD Name ");
            doc.createParagraph().createRun().setText("Sehr geehrte Damen und Herren,");
            doc.createParagraph().createRun().setText("vielen Dank für Ihre Anfrage.");
            write(doc, file);
        }
    }

    /**
     * Vorlage mit verschachtelter Tabelle, IF-Feld mit eingebettetem MERGEFIELD,
     * INCLUDETEXT, Seitenumbruch, Textmarken und Kopfzeile.
     */
    static void writeComplex(Path file) throws IOException {
        try (XWPFDocument doc = new XWPFDocument()) {
            doc.createHeader(HeaderFooterType.DEFAULT).createParagraph().createRun().setText("Firma GmbH");

            XWPFParagraph condition = doc.createParagraph();
            CTP p = condition.getCTP();
            fldChar(p, STFldCharType.BEGIN);
            instr(p, " IF ");
            fldChar(p, STFldCharType.BEGIN);
            instr(p, " MERGEFIELD Anrede ");
            fldChar(p, STFldCharType.SEPARATE);
            p.addNewR().addNewT().setStringValue("«Anrede»");
            fldChar(p, STFldCharType.END);
            instr(p, " = \"Herr\" \"Sehr geehrter Herr\" \"Sehr geehrte Frau\" ");
            fldChar(p, STFldCharType.SEPARATE);
            p.addNewR().addNewT().setStringValue("Sehr geehrter Herr");
            fldChar(p, STFldCharType.END);

            CTBookmark bookmark = p.addNewBookmarkStart();
            bookmark.setName("Betreff");
            bookmark.setId(BigInteger.ONE);
            CTBookmark hidden = p.addNewBookmarkStart();
            hidden.setName("_GoBack");
            hidden.setId(BigInteger.TWO);

            simpleField(doc.createParagraph().getCTP(), " INCLUDETEXT \"baustein.docx\" ");
            simpleField(doc.createParagraph().getCTP(), " PAGE ");

            XWPFTable table = doc.createTable(2, 2);
            XWPFTableCell cell = table.getRow(0).getCell(0);
            cell.insertTable(0, new XWPFTable(cell.getCTTc().addNewTbl(), cell, 1, 1));

            XWPFRun breakRun = doc.createParagraph().createRun();
            breakRun.addBreak(BreakType.PAGE);
            doc.createParagraph().createRun().setText("Seite 2");
            write(doc, file);
        }
    }

    private static void simpleField(CTP paragraph, String instruction) {
        CTSimpleField field = paragraph.addNewFldSimple();
        field.setInstr(instruction);
        field.addNewR().addNewT().setStringValue("Feld");
    }

    private static void fldChar(CTP paragraph, STFldCharType.Enum type) {
        paragraph.addNewR().addNewFldChar().setFldCharType(type);
    }

    private static void instr(CTP paragraph, String text) {
        CTR run = paragraph.addNewR();
        run.addNewInstrText().setStringValue(text);
    }

    private static void write(XWPFDocument doc, Path file) throws IOException {
        try (OutputStream out = Files.newOutputStream(file)) {
            doc.write(out);
        }
    }
}
