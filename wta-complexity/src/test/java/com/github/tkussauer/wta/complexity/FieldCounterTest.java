package com.github.tkussauer.wta.complexity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FieldCounterTest {

    @Test
    void classifiesFieldCodes() {
        assertEquals(Metric.DATA_FIELDS, FieldCounter.classify(" MERGEFIELD Name \\* MERGEFORMAT "));
        assertEquals(Metric.DATA_FIELDS, FieldCounter.classify("docvariable Kunde"));
        assertEquals(Metric.CONDITIONS, FieldCounter.classify("IF 1 = 1 \"a\" \"b\""));
        assertEquals(Metric.CONDITIONS, FieldCounter.classify("IF= \"Herr\" \"a\" \"b\""));
        assertEquals(Metric.INCLUDES, FieldCounter.classify("INCLUDETEXT \"x.docx\""));
        assertEquals(Metric.FORM_FIELDS, FieldCounter.classify("FORMCHECKBOX"));
        assertEquals(Metric.OTHER_FIELDS, FieldCounter.classify("NUMPAGES"));
    }

    @Test
    void parsesBinaryFieldMarkers() {
        // IF-Feld mit eingebettetem MERGEFIELD, danach ein PAGE-Feld
        String text = "Text \u0013 IF \u0013 MERGEFIELD Anrede \u0014Herr\u0015 = \"Herr\" \"a\" \"b\" \u0014a\u0015"
                + " und \u0013 PAGE \u00141\u0015";
        TemplateMetrics metrics = new TemplateMetrics("x.doc", "doc");

        DocMetricsExtractor.countFields(text, metrics);

        assertEquals(1, metrics.get(Metric.CONDITIONS));
        assertEquals(1, metrics.get(Metric.DATA_FIELDS));
        assertEquals(1, metrics.get(Metric.OTHER_FIELDS));
        assertEquals(2, metrics.get(Metric.FIELD_NESTING));
    }
}
