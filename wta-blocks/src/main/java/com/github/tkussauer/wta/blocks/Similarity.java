package com.github.tkussauer.wta.blocks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Aehnlichkeit von Vorlagen.
 * <p>
 * Gemessen wird ueber Folgen von {@value #SHINGLE} Woertern ("Shingles") des gesamten Textes inkl.
 * Feldplatzhaltern: {@code 2 · |gemeinsam| / (|A| + |B|)} (Dice-Koeffizient). Das Mass ist robust
 * gegen verschobene Absaetze und erkennt auch Absaetze mit kleinen Textabweichungen als aehnlich.
 */
final class Similarity {

    static final int SHINGLE = 3;
    private static final int PREVIEW = 3;
    private static final int PREVIEW_LENGTH = 120;

    /**
     * Vergleich zweier Vorlagen.
     *
     * @param similarity  Aehnlichkeit von 0 bis 1
     * @param common      Absaetze, die in beiden vorkommen
     * @param onlyA       Absaetze nur in A
     * @param onlyB       Absaetze nur in B
     * @param previewA    erste abweichende Absaetze in A
     * @param previewB    erste abweichende Absaetze in B
     */
    record Pair(int a, int b, double similarity, int common, int onlyA, int onlyB, String previewA, String previewB) {
    }

    /**
     * Gruppe von Vorlagen, die direkt oder ueber andere Vorlagen die Schwelle erreichen.
     *
     * @param members    Indizes der Vorlagen
     * @param reference  Vorlage mit der hoechsten durchschnittlichen Aehnlichkeit zu den anderen
     */
    record Group(int number, List<Integer> members, int reference, double min, double average) {
    }

    private final List<TemplateText> templates;
    private final List<Set<String>> shingles = new ArrayList<>();
    private final List<Set<String>> paragraphs = new ArrayList<>();
    private final double[][] matrix;

    Similarity(List<TemplateText> templates) {
        this.templates = templates;
        for (TemplateText template : templates) {
            shingles.add(shingles(template));
            Set<String> texts = new LinkedHashSet<>();
            template.paragraphs().forEach(p -> texts.add(p.text()));
            paragraphs.add(texts);
        }
        int n = templates.size();
        matrix = new double[n][n];
        for (int a = 0; a < n; a++) {
            matrix[a][a] = 1.0;
            for (int b = a + 1; b < n; b++) {
                matrix[a][b] = matrix[b][a] = dice(shingles.get(a), shingles.get(b));
            }
        }
    }

    double get(int a, int b) {
        return matrix[a][b];
    }

    /** Paare mit einer Aehnlichkeit ab {@code threshold}, absteigend sortiert. */
    List<Pair> pairs(double threshold) {
        List<Pair> pairs = new ArrayList<>();
        for (int a = 0; a < templates.size(); a++) {
            for (int b = a + 1; b < templates.size(); b++) {
                if (matrix[a][b] >= threshold) {
                    pairs.add(compare(a, b));
                }
            }
        }
        pairs.sort((x, y) -> Double.compare(y.similarity(), x.similarity()));
        return pairs;
    }

    /** Zusammenhaengende Gruppen (ab 2 Vorlagen) ueber alle Paare ab {@code threshold}. */
    List<Group> groups(double threshold) {
        int n = templates.size();
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) {
            parent[i] = i;
        }
        for (int a = 0; a < n; a++) {
            for (int b = a + 1; b < n; b++) {
                if (matrix[a][b] >= threshold) {
                    parent[find(parent, a)] = find(parent, b);
                }
            }
        }
        List<List<Integer>> members = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            members.add(new ArrayList<>());
        }
        for (int i = 0; i < n; i++) {
            members.get(find(parent, i)).add(i);
        }
        List<Group> groups = new ArrayList<>();
        members.stream().filter(m -> m.size() > 1)
                .sorted((x, y) -> Integer.compare(y.size(), x.size()))
                .forEach(m -> groups.add(group(groups.size() + 1, m)));
        return groups;
    }

    /** Index der aehnlichsten anderen Vorlage oder -1. */
    int mostSimilar(int a) {
        int best = -1;
        for (int b = 0; b < templates.size(); b++) {
            if (b != a && (best < 0 || matrix[a][b] > matrix[a][best])) {
                best = b;
            }
        }
        return best;
    }

    private Group group(int number, List<Integer> members) {
        double min = 1.0;
        double sum = 0;
        int pairs = 0;
        int reference = members.get(0);
        double bestAverage = -1;
        for (int a : members) {
            double total = 0;
            for (int b : members) {
                if (a != b) {
                    total += matrix[a][b];
                    if (a < b) {
                        min = Math.min(min, matrix[a][b]);
                        sum += matrix[a][b];
                        pairs++;
                    }
                }
            }
            double average = total / (members.size() - 1);
            if (average > bestAverage) {
                bestAverage = average;
                reference = a;
            }
        }
        return new Group(number, Collections.unmodifiableList(members), reference, min, sum / pairs);
    }

    private Pair compare(int a, int b) {
        Set<String> pa = paragraphs.get(a);
        Set<String> pb = paragraphs.get(b);
        int common = 0;
        List<String> onlyA = new ArrayList<>();
        for (String text : pa) {
            if (pb.contains(text)) {
                common++;
            } else {
                onlyA.add(text);
            }
        }
        List<String> onlyB = new ArrayList<>();
        for (String text : pb) {
            if (!pa.contains(text)) {
                onlyB.add(text);
            }
        }
        return new Pair(a, b, matrix[a][b], common, onlyA.size(), onlyB.size(), preview(onlyA), preview(onlyB));
    }

    private static String preview(List<String> texts) {
        List<String> lines = new ArrayList<>();
        for (String text : texts.subList(0, Math.min(PREVIEW, texts.size()))) {
            lines.add("• " + (text.length() > PREVIEW_LENGTH ? text.substring(0, PREVIEW_LENGTH) + "..." : text));
        }
        if (texts.size() > PREVIEW) {
            lines.add("… und " + (texts.size() - PREVIEW) + " weitere");
        }
        return String.join("\n", lines);
    }

    static Set<String> shingles(TemplateText template) {
        List<String> words = new ArrayList<>();
        for (Paragraph paragraph : template.paragraphs()) {
            for (String word : paragraph.text().toLowerCase(Locale.ROOT).split("\\s+")) {
                if (!word.isEmpty()) {
                    words.add(word);
                }
            }
        }
        Set<String> result = new HashSet<>();
        if (words.size() < SHINGLE) {
            result.addAll(words);
            return result;
        }
        for (int i = 0; i + SHINGLE <= words.size(); i++) {
            result.add(String.join(" ", words.subList(i, i + SHINGLE)));
        }
        return result;
    }

    static double dice(Set<String> a, Set<String> b) {
        if (a.isEmpty() && b.isEmpty()) {
            return 1.0;
        }
        Set<String> smaller = a.size() <= b.size() ? a : b;
        Set<String> larger = smaller == a ? b : a;
        int common = 0;
        for (String shingle : smaller) {
            if (larger.contains(shingle)) {
                common++;
            }
        }
        return 2.0 * common / (a.size() + b.size());
    }

    private static int find(int[] parent, int i) {
        while (parent[i] != i) {
            parent[i] = parent[parent[i]];
            i = parent[i];
        }
        return i;
    }
}
