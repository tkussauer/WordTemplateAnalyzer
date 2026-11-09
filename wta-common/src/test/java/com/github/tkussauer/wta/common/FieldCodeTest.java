package com.github.tkussauer.wta.common;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FieldCodeTest {

    @Test
    void parsesKeywordArgumentsAndSwitches() {
        FieldCode code = FieldCode.parse(" MERGEFIELD \"Geburts datum\" \\@ \"dd.MM.yyyy\" \\* MERGEFORMAT ");
        assertEquals("MERGEFIELD", code.keyword());
        assertEquals("Geburts datum", code.argument(0));
        assertEquals("dd.MM.yyyy", code.switches().get("\\@"));
        assertEquals("MERGEFORMAT", code.switches().get("\\*"));
    }

    @Test
    void keepsNestedFieldsAndQuotesTogether() {
        FieldCode code = FieldCode.parse("IF {MERGEFIELD Anrede} = \"Herr\" \"Sehr geehrter {MERGEFIELD Name}\" \"\"");
        assertEquals("IF", code.keyword());
        assertEquals(List.of("{MERGEFIELD Anrede}", "=", "Herr", "Sehr geehrter {MERGEFIELD Name}", ""),
                code.arguments());
    }

    @Test
    void acceptsKeywordWithoutSpace() {
        FieldCode code = FieldCode.parse("IF= \"Herr\" \"a\" \"b\"");
        assertEquals("IF", code.keyword());
        assertEquals("=", code.argument(0));
    }
}
