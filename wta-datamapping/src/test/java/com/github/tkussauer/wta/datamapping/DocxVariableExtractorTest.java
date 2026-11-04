package com.github.tkussauer.wta.datamapping;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocxVariableExtractorTest {

    @TempDir
    Path tempDir;

    private List<VariableOccurrence> occurrences;

    @BeforeEach
    void extract() throws Exception {
        Path file = tempDir.resolve("vorlage.dotx");
        MappingTemplates.write(file);
        occurrences = new DocxVariableExtractor()
                .extract(file, "vorlage.dotx", DataMappingAnalysis.DEFAULT_PLACEHOLDERS, true)
                .occurrences();
    }

    @Test
    void findsMergeFieldsWithFormats() {
        VariableOccurrence name = find(VariableKind.MERGE_FIELD, "Name", "Hauptdokument");
        assertEquals("Text", name.dataType());
        assertNull(name.format());
        assertTrue(name.context().startsWith("«Name», wohnhaft in"));
        assertEquals("Kopfzeile", find(VariableKind.MERGE_FIELD, "name", "Kopfzeile").area());

        VariableOccurrence date = find(VariableKind.MERGE_FIELD, "Rechnungsdatum", null);
        assertEquals("Datum", date.dataType());
        assertEquals("dd.MM.yyyy", date.format());

        VariableOccurrence amount = find(VariableKind.MERGE_FIELD, "Betrag", null);
        assertEquals("Zahl", amount.dataType());
        assertEquals("#.##0,00", amount.format());
    }

    @Test
    void marksFieldsInsideConditions() {
        VariableOccurrence salutation = find(VariableKind.MERGE_FIELD, "Anrede", null);
        assertTrue(salutation.inCondition());
        assertTrue(salutation.condition().startsWith("IF {MERGEFIELD Anrede} = \"Herr\""), salutation.condition());

        VariableOccurrence lastName = find(VariableKind.MERGE_FIELD, "Nachname", null);
        assertTrue(lastName.inCondition());
        assertEquals("Sehr geehrter Herr Muster", lastName.context());

        assertFalse(find(VariableKind.MERGE_FIELD, "Name", "Hauptdokument").inCondition());
    }

    @Test
    void findsDocumentVariablesAndProperties() {
        assertEquals("DOCVARIABLE", find(VariableKind.DOC_VARIABLE, "Kundennummer", "Hauptdokument").fieldType());
        assertEquals("4711", find(VariableKind.DOC_VARIABLE, "Kundennummer", "Dokumenteinstellungen").value());
        assertTrue(find(VariableKind.DOC_VARIABLE, "Altlast", null).isDefinition());
        assertEquals("DOCPROPERTY", find(VariableKind.DOC_PROPERTY, "Firma", "Hauptdokument").fieldType());
        assertEquals("Muster GmbH", find(VariableKind.DOC_PROPERTY, "Firma", "Dokumenteigenschaften").value());
    }

    @Test
    void findsFormFieldsAndSkipsTheirBookmarks() {
        VariableOccurrence place = find(VariableKind.FORM_FIELD, "Ort", null);
        assertEquals("FORMTEXT", place.fieldType());
        assertEquals("Berlin", place.value());

        VariableOccurrence payment = find(VariableKind.FORM_FIELD, "Zahlungsart", null);
        assertEquals("Auswahlliste", payment.dataType());
        assertEquals("Rechnung | Lastschrift", payment.options());

        assertTrue(lookup(VariableKind.BOOKMARK, "Ort").isEmpty());
        assertTrue(lookup(VariableKind.BOOKMARK, "_GoBack").isEmpty());
        assertEquals("Textmarke", find(VariableKind.BOOKMARK, "Betreff", null).fieldType());
    }

    @Test
    void findsContentControls() {
        VariableOccurrence contract = find(VariableKind.CONTENT_CONTROL, "vertrag.nummer", null);
        assertEquals("Nur-Text", contract.fieldType());
        assertEquals("/vertrag/nummer", contract.binding());
        assertEquals("V-123", contract.context());

        VariableOccurrence start = find(VariableKind.CONTENT_CONTROL, "Beginn", null);
        assertEquals("Datum", start.dataType());
        assertEquals("dd.MM.yyyy", start.format());

        assertEquals("Ja/Nein", find(VariableKind.CONTENT_CONTROL, "Zustimmung", null).dataType());
        // Seitenzahl-Baustein ist keine Variable
        assertEquals(3, occurrences.stream().filter(o -> o.kind() == VariableKind.CONTENT_CONTROL).count());
    }

    @Test
    void findsPlaceholdersButNotFieldResults() {
        assertEquals("«Strasse»", find(VariableKind.PLACEHOLDER, "Strasse", null).fieldType());
        assertEquals("{{ Vorname }}", find(VariableKind.PLACEHOLDER, "Vorname", null).fieldType());
        // «Name» und «Anrede» sind Ergebnisse von MERGEFIELDs, keine Platzhalter
        assertTrue(lookup(VariableKind.PLACEHOLDER, "Name").isEmpty());
        assertTrue(lookup(VariableKind.PLACEHOLDER, "Anrede").isEmpty());
        // PAGE ist keine zu befuellende Variable
        assertTrue(occurrences.stream().noneMatch(o -> "PAGE".equals(o.fieldType())));
    }

    private VariableOccurrence find(VariableKind kind, String name, String area) {
        return occurrences.stream()
                .filter(o -> o.kind() == kind && o.name().equals(name) && (area == null || area.equals(o.area())))
                .findFirst()
                .orElseThrow(() -> new AssertionError("nicht gefunden: " + kind + " " + name + " " + area
                        + " in " + occurrences.stream().map(o -> o.kind() + ":" + o.name() + "@" + o.area()).toList()));
    }

    private Optional<VariableOccurrence> lookup(VariableKind kind, String name) {
        return occurrences.stream().filter(o -> o.kind() == kind && o.name().equals(name)).findFirst();
    }
}
