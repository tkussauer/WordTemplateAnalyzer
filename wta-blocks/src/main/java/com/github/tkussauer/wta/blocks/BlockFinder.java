package com.github.tkussauer.wta.blocks;

import com.github.tkussauer.wta.common.InspireNames;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Findet Absatzfolgen, die in mehreren Vorlagen identisch vorkommen.
 * <ol>
 *     <li>Absaetze mit weniger als {@code minParagraphLetters} Buchstaben (Leerzeilen, «Name», "Seite 1")
 *         werden fuer den Vergleich uebergangen.</li>
 *     <li>Fuer jedes Paar gleicher Absaetze in zwei Vorlagen wird die laengste gemeinsame Folge ab
 *         diesem Absatz bestimmt; eine Folge endet am Ende ihres Bereichs (z. B. Inhalt vor Fusszeile).</li>
 *     <li>Jede Folge wird in allen Vorlagen gesucht; Bloecke unter {@code minBlockLetters} Buchstaben
 *         oder in weniger als {@code minDocuments} Vorlagen entfallen.</li>
 *     <li>Ein Block, der vollstaendig in einem laengeren Block mit denselben Vorlagen enthalten ist,
 *         entfaellt; kommt er in mehr Vorlagen vor, bleibt er als eigener Block erhalten.</li>
 * </ol>
 */
final class BlockFinder {

    private static final String SEPARATOR = "\u0001";
    private static final Pattern PLACEHOLDER = Pattern.compile("«[^»]+»|\\{[^{}]+}");
    private static final int NAME_WORDS = 5;
    private static final int NAME_LENGTH = 40;

    private record Position(int document, int index) {
    }

    private final int minParagraphLetters;
    private final int minBlockLetters;
    private final int minDocuments;

    BlockFinder(int minParagraphLetters, int minBlockLetters, int minDocuments) {
        this.minParagraphLetters = minParagraphLetters;
        this.minBlockLetters = minBlockLetters;
        this.minDocuments = minDocuments;
    }

    List<Block> find(List<TemplateText> templates) {
        List<List<Paragraph>> docs = new ArrayList<>();
        for (TemplateText template : templates) {
            docs.add(template.paragraphs().stream().filter(p -> p.letters() >= minParagraphLetters).toList());
        }
        Map<String, List<Position>> index = new HashMap<>();
        for (int d = 0; d < docs.size(); d++) {
            for (int i = 0; i < docs.get(d).size(); i++) {
                index.computeIfAbsent(docs.get(d).get(i).text(), k -> new ArrayList<>()).add(new Position(d, i));
            }
        }

        // Laengste gemeinsame Folgen je Paar gleicher Absaetze
        Map<String, List<Paragraph>> candidates = new LinkedHashMap<>();
        for (List<Position> positions : index.values()) {
            for (int a = 0; a < positions.size(); a++) {
                for (int b = a + 1; b < positions.size(); b++) {
                    Position p1 = positions.get(a);
                    Position p2 = positions.get(b);
                    if (p1.document() == p2.document()) {
                        continue;
                    }
                    List<Paragraph> d1 = docs.get(p1.document());
                    List<Paragraph> d2 = docs.get(p2.document());
                    // Nur am Anfang einer gemeinsamen Folge beginnen; Folgen bleiben in einem Bereich
                    String area = d1.get(p1.index()).area();
                    if (p1.index() > 0 && p2.index() > 0 && d1.get(p1.index() - 1).area().equals(area)
                            && same(d1.get(p1.index() - 1), d2.get(p2.index() - 1))) {
                        continue;
                    }
                    int length = 0;
                    while (p1.index() + length < d1.size() && p2.index() + length < d2.size()
                            && d1.get(p1.index() + length).area().equals(area)
                            && same(d1.get(p1.index() + length), d2.get(p2.index() + length))) {
                        length++;
                    }
                    List<Paragraph> sequence = d1.subList(p1.index(), p1.index() + length);
                    candidates.putIfAbsent(key(sequence), sequence);
                }
            }
        }

        // Fundstellen in allen Vorlagen, Mindestumfang
        record Found(List<Paragraph> sequence, List<Block.Occurrence> occurrences, Set<String> documents,
                     int letters) {
        }
        List<Found> found = new ArrayList<>();
        for (List<Paragraph> sequence : candidates.values()) {
            int letters = sequence.stream().mapToInt(Paragraph::letters).sum();
            if (letters < minBlockLetters) {
                continue;
            }
            List<Block.Occurrence> occurrences = new ArrayList<>();
            Set<String> documents = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            for (Position start : index.get(sequence.get(0).text())) {
                List<Paragraph> doc = docs.get(start.document());
                if (matches(doc, start.index(), sequence)) {
                    String name = templates.get(start.document()).document();
                    occurrences.add(new Block.Occurrence(name, sequence.get(0).area(),
                            doc.get(start.index()).number(), doc.get(start.index() + sequence.size() - 1).number()));
                    documents.add(name);
                }
            }
            if (documents.size() >= minDocuments) {
                found.add(new Found(sequence, occurrences, documents, letters));
            }
        }

        // Teilbloecke mit denselben Vorlagen wie ein laengerer Block sind ueberfluessig
        found.sort(Comparator.comparingInt((Found f) -> f.sequence().size()).reversed());
        Map<Set<String>, List<String>> keptByDocuments = new HashMap<>();
        List<Found> kept = new ArrayList<>();
        for (Found f : found) {
            String key = SEPARATOR + key(f.sequence()) + SEPARATOR;
            List<String> longer = keptByDocuments.computeIfAbsent(f.documents(), k -> new ArrayList<>());
            if (longer.stream().noneMatch(l -> l.contains(key))) {
                longer.add(key);
                kept.add(f);
            }
        }

        kept.sort(Comparator.comparingLong((Found f) -> (long) f.letters() * (f.documents().size() - 1))
                .reversed().thenComparing(f -> f.sequence().get(0).text()));
        List<Block> blocks = new ArrayList<>();
        Set<String> names = new HashSet<>();
        int number = 0;
        for (Found f : kept) {
            Set<String> areas = new LinkedHashSet<>();
            f.occurrences().forEach(o -> areas.add(o.area()));
            Set<String> fields = new LinkedHashSet<>();
            List<String> texts = new ArrayList<>();
            for (Paragraph paragraph : f.sequence()) {
                texts.add(paragraph.text());
                Matcher matcher = PLACEHOLDER.matcher(paragraph.text());
                while (matcher.find()) {
                    fields.add(matcher.group());
                }
            }
            blocks.add(new Block(String.format("B%03d", ++number), uniqueName(name(areas, texts), names), areas,
                    texts, f.letters(), f.occurrences(), f.documents(), fields));
        }
        return blocks;
    }

    /** Blockname aus Bereich und den ersten Woertern, z. B. Block_MitFreundlichenGruessenIhre. */
    static String name(Set<String> areas, List<String> texts) {
        String prefix = areas.size() == 1 && areas.contains("Kopfzeile") ? "Kopf_"
                : areas.size() == 1 && areas.contains("Fußzeile") ? "Fuss_"
                : areas.size() == 1 && areas.contains("Kopf-/Fußzeile") ? "KopfFuss_" : "Block_";
        String text = PLACEHOLDER.matcher(String.join(" ", texts)).replaceAll(" ");
        StringBuilder words = new StringBuilder();
        int count = 0;
        for (String word : text.split("\\s+")) {
            String id = InspireNames.identifier(word);
            if (id.length() > 1 && !Character.isDigit(word.charAt(0)) && count < NAME_WORDS) {
                words.append(id);
                count++;
            }
        }
        String name = prefix + (words.isEmpty() ? "Text" : words.toString());
        return name.length() > NAME_LENGTH ? name.substring(0, NAME_LENGTH) : name;
    }

    private static String uniqueName(String name, Set<String> used) {
        String candidate = name;
        for (int i = 2; !used.add(candidate.toLowerCase(Locale.ROOT)); i++) {
            candidate = name + "_" + i;
        }
        return candidate;
    }

    private static boolean matches(List<Paragraph> doc, int start, List<Paragraph> sequence) {
        if (start + sequence.size() > doc.size()) {
            return false;
        }
        for (int i = 0; i < sequence.size(); i++) {
            if (!same(doc.get(start + i), sequence.get(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean same(Paragraph a, Paragraph b) {
        return a.text().equals(b.text()) && a.area().equals(b.area());
    }

    private static String key(List<Paragraph> sequence) {
        StringBuilder key = new StringBuilder();
        for (Paragraph paragraph : sequence) {
            if (!key.isEmpty()) {
                key.append(SEPARATOR);
            }
            key.append(paragraph.area()).append(':').append(paragraph.text());
        }
        return key.toString();
    }
}
