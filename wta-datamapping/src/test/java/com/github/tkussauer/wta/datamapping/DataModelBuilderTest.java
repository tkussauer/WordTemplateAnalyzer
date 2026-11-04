package com.github.tkussauer.wta.datamapping;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataModelBuilderTest {

    @Test
    void createsValidInspireIdentifiers() {
        assertEquals("Strasse", DataModelBuilder.identifier("Straße"));
        assertEquals("GeburtsDatum", DataModelBuilder.identifier("Geburts datum"));
        assertEquals("KundeNr", DataModelBuilder.identifier("kunde_Nr"));
        assertEquals("F2Zeile", DataModelBuilder.identifier("2. Zeile"));
        assertEquals("Ueberweisung", DataModelBuilder.identifier("Überweisung"));
        assertEquals("", DataModelBuilder.identifier("--"));
    }

    @Test
    void mergesSpellingVariantsAcrossKindsAndTemplates() {
        List<ModelField> fields = DataModelBuilder.build(List.of(
                template("a.docx",
                        occurrence("a.docx", VariableKind.MERGE_FIELD, "MERGEFIELD", "Strasse"),
                        occurrence("a.docx", VariableKind.PLACEHOLDER, "«Straße»", "Straße")),
                template("b.doc",
                        occurrence("b.doc", VariableKind.CONTENT_CONTROL, "Nur-Text", "strasse"))));

        assertEquals(1, fields.size());
        ModelField street = fields.get(0);
        assertEquals("Data.Adresse.Strasse", street.path());
        assertEquals(3, street.mergedCount());
        assertEquals(2, street.templates().size());
        assertEquals(List.of("MERGEFIELD Strasse", "«Straße»", "Inhaltssteuerelement strasse"),
                List.copyOf(street.sources()));
    }

    @Test
    void takesHierarchyFromNamesAndDataBindings() {
        Map<String, ModelField> byPath = byPath(DataModelBuilder.build(List.of(template("a.docx",
                occurrence("a.docx", VariableKind.PLACEHOLDER, "{{kunde.anschrift.ort}}", "kunde.anschrift.ort"),
                occurrence("a.docx", VariableKind.CONTENT_CONTROL, "Nur-Text", "Policennummer")
                        .binding("/ns0:vertrag[1]/ns0:police"),
                occurrence("a.docx", VariableKind.DOC_PROPERTY, "DOCPROPERTY", "Firma"),
                occurrence("a.docx", VariableKind.MERGE_FIELD, "MERGEFIELD", "Firma")))));

        assertTrue(byPath.containsKey("Data.Kunde.Anschrift.Ort"), byPath.keySet().toString());
        assertTrue(byPath.containsKey("Data.Vertrag.Police"));
        // gleicher Name, aber verschiedene Bedeutung: nicht zusammengefuehrt, aber als Hinweis
        assertTrue(byPath.containsKey("Data.Dokument.Firma"));
        assertTrue(byPath.get("Data.Empfaenger.Firma").notes().stream().anyMatch(n -> n.contains("Dublette")));
    }

    @Test
    void derivesInspireTypes() {
        Map<String, ModelField> byName = DataModelBuilder.build(List.of(template("a.docx",
                occurrence("a.docx", VariableKind.MERGE_FIELD, "MERGEFIELD", "Betrag").dataType(DataTypes.NUMBER),
                occurrence("a.docx", VariableKind.MERGE_FIELD, "MERGEFIELD", "Anzahl").dataType(DataTypes.NUMBER),
                occurrence("a.docx", VariableKind.MERGE_FIELD, "MERGEFIELD", "Faktor").dataType(DataTypes.NUMBER)
                        .format("0,00"),
                occurrence("a.docx", VariableKind.MERGE_FIELD, "MERGEFIELD", "Stichtag")
                        .dataType(DataTypes.DATE + DataTypes.GUESSED),
                occurrence("a.docx", VariableKind.FORM_FIELD, "FORMCHECKBOX", "Zustimmung")
                        .dataType(DataTypes.BOOLEAN),
                occurrence("a.docx", VariableKind.CONTENT_CONTROL, "Wiederholungsabschnitt", "Positionen")
                        .dataType("Liste (wiederholend)"),
                occurrence("a.docx", VariableKind.BOOKMARK, "SET", "Summe2").dataType("berechnet (SET)"))))
                .stream().collect(Collectors.toMap(ModelField::name, Function.identity()));

        assertEquals(InspireType.CURRENCY, byName.get("Betrag").type());
        assertEquals(InspireType.INT, byName.get("Anzahl").type());
        assertEquals(InspireType.DOUBLE, byName.get("Faktor").type());
        assertEquals(InspireType.DATE_TIME, byName.get("Stichtag").type());
        assertTrue(byName.get("Stichtag").notes().contains("Datentyp aus dem Namen abgeleitet"));
        assertEquals(InspireType.BOOL, byName.get("Zustimmung").type());
        assertEquals(InspireType.ARRAY, byName.get("Positionen").type());
        assertTrue(byName.get("Summe2").notes().get(0).startsWith("Berechneter Wert"));
    }

    @Test
    void resolvesTypeConflictsAndUsage() {
        List<ModelField> fields = DataModelBuilder.build(List.of(
                template("a.docx", occurrence("a.docx", VariableKind.MERGE_FIELD, "MERGEFIELD", "Beginn")
                        .dataType(DataTypes.TEXT).inCondition(true)),
                template("b.docx", occurrence("b.docx", VariableKind.MERGE_FIELD, "MERGEFIELD", "Beginn")
                        .dataType(DataTypes.DATE).format("dd.MM.yyyy").inCondition(true))));

        ModelField start = fields.get(0);
        assertEquals("Data.Vertrag.Beginn", start.path());
        assertEquals(InspireType.DATE_TIME, start.type());
        assertTrue(start.conditional());
        assertTrue(start.notes().get(0).startsWith("Uneinheitlicher Datentyp"));
    }

    @Test
    void skipsUnnamedAndContainerVariables() {
        List<ModelField> fields = DataModelBuilder.build(List.of(template("a.docx",
                occurrence("a.docx", VariableKind.CONTENT_CONTROL, "Rich-Text", "(ohne Titel)"),
                occurrence("a.docx", VariableKind.FORM_FIELD, "FORMTEXT", "(ohne Namen)"),
                occurrence("a.docx", VariableKind.CONTENT_CONTROL, "Gruppe", "Block").dataType("Gruppe"),
                occurrence("a.docx", VariableKind.BOOKMARK, "Textmarke", "Sprungziel"))));

        assertEquals(1, fields.size());
        assertFalse(fields.get(0).conditional());
        assertTrue(fields.get(0).notes().get(0).startsWith("Nur Textmarke"));
    }

    private static TemplateVariables template(String name, VariableOccurrence... occurrences) {
        return new TemplateVariables(name, "docx", List.of(occurrences));
    }

    private static VariableOccurrence occurrence(String document, VariableKind kind, String fieldType, String name) {
        return new VariableOccurrence(document, "Hauptdokument", kind, fieldType, name);
    }

    private static Map<String, ModelField> byPath(List<ModelField> fields) {
        return fields.stream().collect(Collectors.toMap(ModelField::path, Function.identity()));
    }
}
