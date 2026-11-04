package com.github.tkussauer.wta.datamapping;

import org.apache.poi.wp.usermodel.HeaderFooterType;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlOptions;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBody;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Erzeugt eine Testvorlage mit allen unterstuetzten Variablenarten.
 */
final class MappingTemplates {

    private static final String W = DocxVariableExtractor.W;

    private static final String BODY = """
            <w:body xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"
                    xmlns:w14="http://schemas.microsoft.com/office/word/2010/wordml">
              <w:p>
                <w:fldSimple w:instr=" MERGEFIELD Name \\* MERGEFORMAT "><w:r><w:t>«Name»</w:t></w:r></w:fldSimple>
                <w:r><w:t xml:space="preserve">, wohnhaft in «Strasse» und {{ Vorname }}</w:t></w:r>
              </w:p>
              <w:p>
                <w:r><w:fldChar w:fldCharType="begin"/></w:r>
                <w:r><w:instrText xml:space="preserve"> IF </w:instrText></w:r>
                <w:r><w:fldChar w:fldCharType="begin"/></w:r>
                <w:r><w:instrText xml:space="preserve"> MERGEFIELD Anrede </w:instrText></w:r>
                <w:r><w:fldChar w:fldCharType="separate"/></w:r>
                <w:r><w:t>«Anrede»</w:t></w:r>
                <w:r><w:fldChar w:fldCharType="end"/></w:r>
                <w:r><w:instrText xml:space="preserve"> = "Herr" "Sehr geehrter Herr </w:instrText></w:r>
                <w:r><w:fldChar w:fldCharType="begin"/></w:r>
                <w:r><w:instrText xml:space="preserve"> MERGEFIELD Nachname </w:instrText></w:r>
                <w:r><w:fldChar w:fldCharType="separate"/></w:r>
                <w:r><w:t>«Nachname»</w:t></w:r>
                <w:r><w:fldChar w:fldCharType="end"/></w:r>
                <w:r><w:instrText xml:space="preserve">" "Sehr geehrte Damen und Herren" </w:instrText></w:r>
                <w:r><w:fldChar w:fldCharType="separate"/></w:r>
                <w:r><w:t>Sehr geehrter Herr Muster</w:t></w:r>
                <w:r><w:fldChar w:fldCharType="end"/></w:r>
              </w:p>
              <w:p>
                <w:r><w:t xml:space="preserve">Rechnung vom </w:t></w:r>
                <w:fldSimple w:instr=" MERGEFIELD Rechnungsdatum \\@ &quot;dd.MM.yyyy&quot; "><w:r><w:t>01.01.2026</w:t></w:r></w:fldSimple>
                <w:r><w:t xml:space="preserve"> über </w:t></w:r>
                <w:fldSimple w:instr=" MERGEFIELD Betrag \\# &quot;#.##0,00&quot; "><w:r><w:t>1,00</w:t></w:r></w:fldSimple>
                <w:r><w:t xml:space="preserve"> Kunde </w:t></w:r>
                <w:fldSimple w:instr=" DOCVARIABLE Kundennummer "><w:r><w:t>4711</w:t></w:r></w:fldSimple>
                <w:fldSimple w:instr=" DOCPROPERTY Firma "><w:r><w:t>Muster GmbH</w:t></w:r></w:fldSimple>
                <w:fldSimple w:instr=" PAGE "><w:r><w:t>1</w:t></w:r></w:fldSimple>
              </w:p>
              <w:p>
                <w:bookmarkStart w:id="1" w:name="Ort"/>
                <w:r><w:fldChar w:fldCharType="begin">
                  <w:ffData><w:name w:val="Ort"/><w:textInput><w:default w:val="Berlin"/></w:textInput></w:ffData>
                </w:fldChar></w:r>
                <w:r><w:instrText xml:space="preserve"> FORMTEXT </w:instrText></w:r>
                <w:r><w:fldChar w:fldCharType="separate"/></w:r>
                <w:r><w:t>Berlin</w:t></w:r>
                <w:r><w:fldChar w:fldCharType="end"/></w:r>
                <w:bookmarkEnd w:id="1"/>
                <w:r><w:fldChar w:fldCharType="begin">
                  <w:ffData><w:name w:val="Zahlungsart"/>
                    <w:ddList><w:listEntry w:val="Rechnung"/><w:listEntry w:val="Lastschrift"/></w:ddList></w:ffData>
                </w:fldChar></w:r>
                <w:r><w:instrText xml:space="preserve"> FORMDROPDOWN </w:instrText></w:r>
                <w:r><w:fldChar w:fldCharType="end"/></w:r>
                <w:bookmarkStart w:id="2" w:name="Betreff"/><w:bookmarkEnd w:id="2"/>
                <w:bookmarkStart w:id="3" w:name="_GoBack"/><w:bookmarkEnd w:id="3"/>
              </w:p>
              <w:sdt>
                <w:sdtPr><w:alias w:val="Vertragsnummer"/><w:tag w:val="vertrag.nummer"/><w:text/>
                  <w:dataBinding w:xpath="/vertrag/nummer" w:storeItemID="{1}"/></w:sdtPr>
                <w:sdtContent><w:p><w:r><w:t>V-123</w:t></w:r></w:p></w:sdtContent>
              </w:sdt>
              <w:sdt>
                <w:sdtPr><w:alias w:val="Beginn"/><w:date><w:dateFormat w:val="dd.MM.yyyy"/></w:date></w:sdtPr>
                <w:sdtContent><w:p><w:r><w:t>01.02.2026</w:t></w:r></w:p></w:sdtContent>
              </w:sdt>
              <w:sdt>
                <w:sdtPr><w:tag w:val="Zustimmung"/><w14:checkbox/></w:sdtPr>
                <w:sdtContent><w:p><w:r><w:t>☐</w:t></w:r></w:p></w:sdtContent>
              </w:sdt>
              <w:sdt>
                <w:sdtPr><w:docPartObj><w:docPartGallery w:val="Page Numbers (Bottom of Page)"/></w:docPartObj></w:sdtPr>
                <w:sdtContent><w:p><w:r><w:t>Seite</w:t></w:r></w:p></w:sdtContent>
              </w:sdt>
              <w:sectPr/>
            </w:body>
            """;

    private MappingTemplates() {
    }

    static void write(Path file) throws IOException {
        try (XWPFDocument doc = new XWPFDocument()) {
            try {
                // Wurzelelement w:body beim Laden entfernen, sonst entsteht w:body in w:body
                doc.getDocument().getBody().set(CTBody.Factory.parse(BODY,
                        new XmlOptions().setLoadReplaceDocumentElement(null)));
            } catch (XmlException e) {
                throw new IOException(e);
            }
            doc.createHeader(HeaderFooterType.DEFAULT).createParagraph().getCTP()
                    .addNewFldSimple().setInstr(" MERGEFIELD name ");
            doc.getProperties().getCustomProperties().addProperty("Firma", "Muster GmbH");
            try (OutputStream out = Files.newOutputStream(file)) {
                doc.write(out);
            }
        }
        addDocumentVariables(file, "<w:docVars xmlns:w=\"" + W + "\">"
                + "<w:docVar w:name=\"Kundennummer\" w:val=\"4711\"/><w:docVar w:name=\"Altlast\" w:val=\"x\"/>"
                + "</w:docVars>");
    }

    /**
     * Fuegt Dokumentvariablen direkt in word/settings.xml ein; die Schemaklasse dafuer fehlt in
     * poi-ooxml-lite, sodass sie nicht ueber die POI-API angelegt werden koennen.
     */
    private static void addDocumentVariables(Path file, String docVars) throws IOException {
        Path copy = file.resolveSibling(file.getFileName() + ".tmp");
        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(file));
             ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(copy))) {
            for (ZipEntry entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
                byte[] data = in.readAllBytes();
                if (entry.getName().equals("word/settings.xml")) {
                    String xml = new String(data, StandardCharsets.UTF_8);
                    int end = xml.lastIndexOf("</");
                    xml = end >= 0
                            ? xml.substring(0, end) + docVars + xml.substring(end)
                            // POI schreibt ein leeres Element: <w:settings .../>
                            : xml.substring(0, xml.lastIndexOf("/>")) + ">" + docVars + "</w:settings>";
                    data = xml.getBytes(StandardCharsets.UTF_8);
                }
                out.putNextEntry(new ZipEntry(entry.getName()));
                out.write(data);
                out.closeEntry();
            }
        }
        Files.move(copy, file, StandardCopyOption.REPLACE_EXISTING);
    }
}
