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
    void countsDistinctDataFieldsAndAllUses() {
        TemplateMetrics metrics = new TemplateMetrics("x.docx", "docx");
        FieldCounter fields = new FieldCounter(metrics);
        fields.simple(" MERGEFIELD Name \\* MERGEFORMAT ");
        fields.simple("MERGEFIELD  name");
        fields.simple("MERGEFIELD \"Geburts datum\"");
        fields.simple("MERGEFIELD \"Geburts datum\" \\@ \"dd.MM.yyyy\"");
        fields.simple("DOCVARIABLE Name");
        fields.finish();

        // Name und name sind dasselbe Feld, DOCVARIABLE Name ein anderes
        assertEquals(3, metrics.get(Metric.DATA_FIELDS));
        assertEquals(5, metrics.get(Metric.DATA_FIELD_USES));
    }

    @Test
    void separatesContentAndHeaderFooter() {
        TemplateMetrics metrics = new TemplateMetrics("x.docx", "docx");
        FieldCounter fields = new FieldCounter(metrics);
        fields.simple("MERGEFIELD Name");
        fields.begin();
        fields.code(" IF ");
        fields.begin();
        fields.code(" MERGEFIELD Anrede ");
        fields.separate();
        fields.end();
        fields.code(" = \"Herr\" \"a\" \"b\" ");
        fields.separate();
        fields.end();
        fields.region(Region.HEADER_FOOTER);
        fields.simple("MERGEFIELD name");
        fields.simple("MERGEFIELD Name");
        fields.simple("MERGEFIELD Vorgang");
        fields.finish();

        assertEquals(3, metrics.get(Metric.DATA_FIELDS));
        assertEquals(2, metrics.get(Metric.DATA_FIELDS_CONTENT));
        assertEquals(2, metrics.get(Metric.DATA_FIELDS_HEADER_FOOTER));
        assertEquals(1, metrics.get(Metric.CONDITIONS));
        assertEquals(1, metrics.get(Metric.CONDITIONS_CONTENT));
        assertEquals(0, metrics.get(Metric.CONDITIONS_HEADER_FOOTER));

        FieldEntry condition = metrics.fieldEntries().stream().filter(FieldEntry::condition).findFirst().orElseThrow();
        assertEquals(Region.CONTENT, condition.region());
        assertEquals("IF", condition.keyword());
        // verschachteltes Feld bleibt im Feldcode der Bedingung sichtbar
        assertEquals("IF {MERGEFIELD Anrede} = \"Herr\" \"a\" \"b\"", condition.name());

        FieldEntry headerName = metrics.fieldEntries().stream()
                .filter(e -> e.region() == Region.HEADER_FOOTER && e.name().equalsIgnoreCase("name"))
                .findFirst().orElseThrow();
        // Name und name sind dasselbe Feld: ein Eintrag mit zwei Vorkommen
        assertEquals(2, headerName.occurrences());
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
