package com.github.tkussauer.wta.conditions;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConditionParserTest {

    private static FieldTreeBuilder builder() {
        FieldTreeBuilder builder = new FieldTreeBuilder();
        builder.paragraphStart();
        return builder;
    }

    /** Einfaches Feld ohne Verschachtelung, wie es die Leser als Ereignisse liefern. */
    private static void field(FieldTreeBuilder b, String code) {
        b.beginField();
        b.fieldCode(code);
        b.separateField();
        b.endField();
    }

    @Test
    void parsesNestedConditionWithBranches() {
        FieldTreeBuilder b = builder();
        b.text("Anrede: ");
        b.beginField();
        b.fieldCode(" IF ");
        field(b, " MERGEFIELD Anrede ");
        b.fieldCode(" = \"Herr\" \"Sehr geehrter Herr ");
        field(b, " MERGEFIELD Name ");
        b.fieldCode("\" \"");
        b.beginField();
        b.fieldCode(" IF ");
        field(b, " MERGEFIELD Anrede ");
        b.fieldCode(" = \"Frau\" \"Sehr geehrte Frau\" \"Guten Tag\" ");
        b.separateField();
        b.endField();
        b.fieldCode("\" ");
        b.separateField();
        b.text("Sehr geehrter Herr Muster");
        b.endField();
        b.paragraphEnd();

        List<Condition> conditions = ConditionParser.parse("a.docx", b.finish());

        assertEquals(2, conditions.size());
        Condition outer = conditions.get(0);
        assertEquals("1", outer.id());
        assertEquals("IF", outer.kind());
        assertEquals(List.of("MERGEFIELD Anrede"), outer.conditionFields());
        // Felder in verschachtelten Bedingungen gehoeren zu diesen, nicht zur aeusseren Bedingung
        assertEquals(List.of("MERGEFIELD Name"), outer.textFields());
        assertEquals("=", outer.operator());
        assertEquals("Herr", outer.compareValue());
        assertEquals("„Sehr geehrter Herr «Name»“", outer.thenText());
        assertEquals("[Bedingung 1.1]", outer.elseText());
        assertEquals("WENN «Anrede» ist gleich „Herr“ DANN „Sehr geehrter Herr «Name»“ SONST [Bedingung 1.1]",
                outer.readable());
        assertEquals("Anrede == \"Herr\"", outer.expression());
        assertEquals("Sehr geehrter Herr Muster", outer.result());
        assertEquals("Anrede: Sehr geehrter Herr Muster", outer.context());
        assertTrue(outer.notes().contains("Enthält verschachtelte Bedingungen"));

        Condition inner = conditions.get(1);
        assertEquals("1.1", inner.id());
        assertEquals(2, inner.level());
        assertEquals("Sonst-Zweig von 1", inner.position());
        assertEquals("WENN «Anrede» ist gleich „Frau“ DANN „Sehr geehrte Frau“ SONST „Guten Tag“", inner.readable());
    }

    @Test
    void handlesOperatorWithoutSpacesAndEmptyComparison() {
        FieldTreeBuilder b = builder();
        // So speichert Word "ist nicht leer": IF {MERGEFIELD X}<> """{MERGEFIELD X}" ""
        b.beginField();
        b.fieldCode(" IF ");
        field(b, " MERGEFIELD Zusatz ");
        b.fieldCode("<> \"\"\"");
        field(b, " MERGEFIELD Zusatz ");
        b.fieldCode("\" \"\" ");
        b.endField();

        Condition condition = ConditionParser.parse("a.docx", b.finish()).get(0);

        assertEquals("<>", condition.operator());
        assertEquals("", condition.compareValue());
        assertEquals("WENN «Zusatz» ist nicht leer DANN «Zusatz» SONST (nichts)", condition.readable());
        assertEquals("Zusatz != \"\"", condition.expression());
    }

    @Test
    void parsesCompareSkipIfAndNumbers() {
        FieldTreeBuilder b = builder();
        b.beginField();
        b.fieldCode(" COMPARE ");
        field(b, " MERGEFIELD Betrag ");
        b.fieldCode(" >= 1000 ");
        b.endField();
        b.beginField();
        b.fieldCode(" SKIPIF ");
        field(b, " MERGEFIELD Land ");
        b.fieldCode(" <> \"D*\" ");
        b.endField();

        List<Condition> conditions = ConditionParser.parse("a.docx", b.finish());

        assertEquals("2", conditions.get(1).id());
        Condition compare = conditions.get(0);
        assertEquals("VERGLEICH: «Betrag» ist größer oder gleich 1000 (Ergebnis 1 oder 0)", compare.readable());
        assertEquals("Betrag >= 1000", compare.expression());
        assertNull(compare.thenText());

        Condition skip = conditions.get(1);
        assertEquals("Datensatz ÜBERSPRINGEN, WENN «Land» ist ungleich „D*“", skip.readable());
        assertTrue(skip.notes().get(0).startsWith("Platzhaltervergleich"));
    }

    @Test
    void reportsUnparsableConditions() {
        FieldTreeBuilder b = builder();
        field(b, " IF ");
        Condition condition = ConditionParser.parse("a.docx", b.finish()).get(0);
        assertTrue(condition.notes().get(0).startsWith("Bedingung konnte nicht vollständig zerlegt werden"));
        assertNull(condition.expression());
        assertEquals("WENN (fehlt) (fehlt) DANN (nichts) SONST (nichts)", condition.readable());
    }

    @Test
    void ignoresOtherFields() {
        FieldTreeBuilder b = builder();
        field(b, " MERGEFIELD Name ");
        field(b, " PAGE ");
        assertTrue(ConditionParser.parse("a.docx", b.finish()).isEmpty());
    }
}
