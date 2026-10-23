package com.github.tkussauer.wta.complexity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComplexityModelTest {

    @TempDir
    Path tempDir;

    @Test
    void classifiesByPointThresholds() throws Exception {
        ComplexityModel model = ComplexityModel.defaults();
        assertEquals(TShirtSize.S, model.sizeFor(0));
        assertEquals(TShirtSize.S, model.sizeFor(19.99));
        assertEquals(TShirtSize.M, model.sizeFor(20));
        assertEquals(TShirtSize.L, model.sizeFor(50));
        assertEquals(TShirtSize.XL, model.sizeFor(250));
    }

    @Test
    void sumsWeightedPointsAndNamesDrivers() throws Exception {
        ComplexityModel model = ComplexityModel.defaults();
        TemplateMetrics metrics = new TemplateMetrics("a.docx", "docx")
                .set(Metric.PAGES, 3)          // 6
                .set(Metric.CONDITIONS, 4)     // 16
                .set(Metric.DATA_FIELDS, 10);  // 5

        Assessment assessment = model.assess(metrics);

        assertEquals(27.0, assessment.totalPoints(), 1e-9);
        assertEquals(TShirtSize.M, assessment.size());
        assertEquals(List.of(Metric.CONDITIONS, Metric.PAGES, Metric.DATA_FIELDS), assessment.drivers());
        assertTrue(assessment.minimumRules().isEmpty());
    }

    @Test
    void minimumSizeRaisesClassification() throws Exception {
        ComplexityModel model = ComplexityModel.defaults();
        Assessment assessment = model.assess(new TemplateMetrics("m.docm", "docm").set(Metric.MACROS, 1));

        // 15 Punkte waeren S, Makros heben auf mindestens L an
        assertEquals(TShirtSize.S, assessment.sizeByPoints());
        assertEquals(TShirtSize.L, assessment.size());
        assertEquals(List.of(Metric.MACROS), assessment.minimumRules());
    }

    @Test
    void customConfigurationOverridesDefaults() throws Exception {
        Path config = tempDir.resolve("schema.properties");
        Files.writeString(config, "schwelle.M=5\ngewicht.seiten=10\nmindestgroesse.makros=\n");

        ComplexityModel model = ComplexityModel.load(config);

        assertEquals(10.0, model.weight(Metric.PAGES));
        assertEquals(4.0, model.weight(Metric.CONDITIONS));
        assertEquals(TShirtSize.M, model.assess(new TemplateMetrics("a", "docx").set(Metric.PAGES, 1)).size());
        assertEquals(TShirtSize.M, model.assess(new TemplateMetrics("m", "docm").set(Metric.MACROS, 1)).size());
    }

    @Test
    void rejectsInvalidConfiguration() throws Exception {
        Path config = tempDir.resolve("schema.properties");
        Files.writeString(config, "schwelle.L=10\n");
        assertThrows(IllegalArgumentException.class, () -> ComplexityModel.load(config));

        Files.writeString(config, "mindestgroesse.makros=XXL\n");
        assertThrows(IllegalArgumentException.class, () -> ComplexityModel.load(config));
    }
}
