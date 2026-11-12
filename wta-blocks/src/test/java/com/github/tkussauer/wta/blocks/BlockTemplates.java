package com.github.tkussauer.wta.blocks;

import org.apache.poi.wp.usermodel.HeaderFooterType;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSimpleField;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Testvorlagen: A und B fast gleich, C anders, alle mit gleichem Rechtsblock und gleicher Fusszeile.
 */
final class BlockTemplates {

    static final String INTRO = "vielen Dank für Ihr Interesse an unserem Stromtarif. Gerne bestätigen wir Ihnen "
            + "hiermit den Eingang Ihres Auftrags vom letzten Monat.";
    static final List<String> LEGAL = List.of(
            "Widerrufsbelehrung: Sie haben das Recht, binnen vierzehn Tagen ohne Angabe von Gründen diesen "
                    + "Vertrag zu widerrufen.",
            "Die Widerrufsfrist beträgt vierzehn Tage ab dem Tag des Vertragsabschlusses.",
            "Um Ihr Widerrufsrecht auszuüben, müssen Sie uns mittels einer eindeutigen Erklärung über Ihren "
                    + "Entschluss informieren.");
    static final String FOOTER = "Musterfirma GmbH, Musterstraße 1, 12345 Musterstadt, Amtsgericht Musterstadt HRB 1234";

    private BlockTemplates() {
    }

    static void writeAll(Path directory) throws IOException {
        write(directory.resolve("a.docx"), INTRO, "4711",
                "Für Rückfragen stehen wir Ihnen jederzeit gerne zur Verfügung.");
        write(directory.resolve("b.docx"), INTRO, "0815",
                "Bei Fragen erreichen Sie uns montags bis freitags telefonisch.");
        write(directory.resolve("c.docx"), "leider müssen wir Ihnen mitteilen, dass die Abschlagszahlung für "
                        + "den Monat Oktober noch nicht bei uns eingegangen ist. Bitte überweisen Sie den Betrag.",
                "1234", "Sollten Sie bereits gezahlt haben, betrachten Sie dieses Schreiben als gegenstandslos.");
    }

    private static void write(Path file, String intro, String contractNumber, String closing) throws IOException {
        try (XWPFDocument doc = new XWPFDocument()) {
            doc.createParagraph().createRun().setText("Sehr geehrte Damen und Herren,");
            doc.createParagraph().createRun().setText(intro);
            // Feld mit unterschiedlichem Ergebnis: fuer den Vergleich zaehlt nur der Platzhalter
            XWPFParagraph number = doc.createParagraph();
            number.createRun().setText("Ihre Vertragsnummer lautet ");
            CTSimpleField field = number.getCTP().addNewFldSimple();
            field.setInstr(" MERGEFIELD Vertragsnummer \\* MERGEFORMAT ");
            field.addNewR().addNewT().setStringValue(contractNumber);
            number.createRun().setText(" und gilt für alle Rückfragen.");
            for (String text : LEGAL) {
                doc.createParagraph().createRun().setText(text);
            }
            doc.createParagraph().createRun().setText(closing);
            doc.createParagraph().createRun().setText("Mit freundlichen Grüßen");
            doc.createFooter(HeaderFooterType.DEFAULT).createParagraph().createRun().setText(FOOTER);
            try (OutputStream out = Files.newOutputStream(file)) {
                doc.write(out);
            }
        }
    }
}
