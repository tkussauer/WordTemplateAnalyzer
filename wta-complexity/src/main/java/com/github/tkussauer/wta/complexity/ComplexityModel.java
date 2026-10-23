package com.github.tkussauer.wta.complexity;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Bewertungsschema: Gewichte je Kennzahl, Punkteschwellen und Mindestgroessen.
 */
public final class ComplexityModel {

    private static final String DEFAULTS = "komplexitaet-standard.properties";
    private static final int DRIVER_COUNT = 3;

    private final Map<Metric, Double> weights = new EnumMap<>(Metric.class);
    private final Map<TShirtSize, Double> thresholds = new EnumMap<>(TShirtSize.class);
    private final Map<Metric, TShirtSize> minimumSizes = new EnumMap<>(Metric.class);

    private ComplexityModel(Properties properties) {
        for (Metric metric : Metric.values()) {
            weights.put(metric, number(properties, "gewicht." + metric.key(), 0));
            String minimum = properties.getProperty("mindestgroesse." + metric.key());
            if (minimum != null && !minimum.isBlank()) {
                minimumSizes.put(metric, TShirtSize.parse(minimum));
            }
        }
        thresholds.put(TShirtSize.S, 0.0);
        for (TShirtSize size : List.of(TShirtSize.M, TShirtSize.L, TShirtSize.XL)) {
            thresholds.put(size, number(properties, "schwelle." + size.name(), Double.NaN));
        }
        if (!(thresholds.get(TShirtSize.M) < thresholds.get(TShirtSize.L)
                && thresholds.get(TShirtSize.L) < thresholds.get(TShirtSize.XL))) {
            throw new IllegalArgumentException("Die Schwellen müssen aufsteigend sein: schwelle.M < schwelle.L < schwelle.XL");
        }
    }

    /** Standardschema aus {@value #DEFAULTS}. */
    public static ComplexityModel defaults() throws IOException {
        return new ComplexityModel(loadDefaults());
    }

    /** Standardschema, ueberschrieben durch die Werte aus {@code file}. */
    public static ComplexityModel load(Path file) throws IOException {
        Properties properties = loadDefaults();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return new ComplexityModel(properties);
    }

    private static Properties loadDefaults() throws IOException {
        Properties properties = new Properties();
        try (InputStream in = ComplexityModel.class.getResourceAsStream(DEFAULTS)) {
            if (in == null) {
                throw new IOException("Standard-Bewertungsschema nicht gefunden: " + DEFAULTS);
            }
            properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        return properties;
    }

    private static double number(Properties properties, String key, double fallback) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            if (Double.isNaN(fallback)) {
                throw new IllegalArgumentException("Wert fehlt im Bewertungsschema: " + key);
            }
            return fallback;
        }
        try {
            return Double.parseDouble(value.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Keine Zahl im Bewertungsschema: " + key + "=" + value);
        }
    }

    public double weight(Metric metric) {
        return weights.get(metric);
    }

    /** Punkte, ab denen die Groesse gilt (S: 0). */
    public double threshold(TShirtSize size) {
        return thresholds.get(size);
    }

    public TShirtSize minimumSize(Metric metric) {
        return minimumSizes.get(metric);
    }

    public Assessment assess(TemplateMetrics metrics) {
        Map<Metric, Double> points = new EnumMap<>(Metric.class);
        double total = 0;
        for (Metric metric : Metric.values()) {
            double value = metrics.get(metric) * weights.get(metric);
            points.put(metric, value);
            total += value;
        }
        TShirtSize byPoints = sizeFor(total);
        TShirtSize size = byPoints;
        List<Metric> rules = new ArrayList<>();
        for (Map.Entry<Metric, TShirtSize> rule : minimumSizes.entrySet()) {
            if (metrics.get(rule.getKey()) > 0 && rule.getValue().ordinal() > byPoints.ordinal()) {
                rules.add(rule.getKey());
                size = size.max(rule.getValue());
            }
        }
        List<Metric> drivers = points.entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .sorted(Map.Entry.<Metric, Double>comparingByValue(Comparator.reverseOrder()))
                .limit(DRIVER_COUNT)
                .map(Map.Entry::getKey)
                .toList();
        return new Assessment(metrics, Collections.unmodifiableMap(points), total, size, byPoints, rules, drivers);
    }

    TShirtSize sizeFor(double points) {
        TShirtSize result = TShirtSize.S;
        for (TShirtSize size : TShirtSize.values()) {
            if (points >= thresholds.get(size)) {
                result = size;
            }
        }
        return result;
    }
}
