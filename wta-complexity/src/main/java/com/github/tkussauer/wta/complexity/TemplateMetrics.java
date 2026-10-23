package com.github.tkussauer.wta.complexity;

import java.util.EnumMap;
import java.util.Map;

/**
 * Gemessene Kennzahlen einer Vorlage.
 */
public final class TemplateMetrics {

    private final String document;
    private final String format;
    private final Map<Metric, Integer> values = new EnumMap<>(Metric.class);

    public TemplateMetrics(String document, String format) {
        this.document = document;
        this.format = format;
        for (Metric metric : Metric.values()) {
            values.put(metric, 0);
        }
    }

    public String document() {
        return document;
    }

    /** Dateiformat, z. B. "docx" oder "doc". */
    public String format() {
        return format;
    }

    public int get(Metric metric) {
        return values.get(metric);
    }

    public TemplateMetrics set(Metric metric, int value) {
        values.put(metric, value);
        return this;
    }

    void add(Metric metric, int delta) {
        values.merge(metric, delta, Integer::sum);
    }
}
