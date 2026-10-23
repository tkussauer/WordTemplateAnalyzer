package com.github.tkussauer.wta.fonts;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fasst einzelne Textabschnitte eines Dokuments zu {@link FontUsage}-Eintraegen zusammen.
 */
final class UsageCollector {

    private static final int SAMPLE_LENGTH = 60;

    private record Key(String area, String paragraphStyle, String characterStyle, boolean direct, FontStyle style) {
    }

    private static final class Counter {
        int runs;
        int chars;
        final StringBuilder sample = new StringBuilder();
    }

    private final Map<Key, Counter> counters = new LinkedHashMap<>();

    void add(String area, String paragraphStyle, String characterStyle, boolean direct, FontStyle style, String text) {
        Counter counter = counters.computeIfAbsent(
                new Key(area, paragraphStyle, characterStyle, direct, style), k -> new Counter());
        counter.runs++;
        counter.chars += text.length();
        if (counter.sample.length() < SAMPLE_LENGTH) {
            counter.sample.append(text);
        }
    }

    List<FontUsage> toUsages() {
        List<FontUsage> usages = new ArrayList<>(counters.size());
        counters.forEach((key, counter) -> usages.add(new FontUsage(
                key.area(), key.paragraphStyle(), key.characterStyle(), key.direct(), key.style(),
                counter.runs, counter.chars, sample(counter.sample))));
        return usages;
    }

    private static String sample(StringBuilder text) {
        String normalized = text.toString().replaceAll("\\s+", " ").strip();
        if (normalized.length() > SAMPLE_LENGTH) {
            return normalized.substring(0, SAMPLE_LENGTH) + "...";
        }
        return normalized;
    }
}
