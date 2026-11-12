package com.github.tkussauer.wta.blocks;

import java.util.List;
import java.util.Set;

/**
 * Eine Folge von Absaetzen, die in mehreren Vorlagen identisch vorkommt; Kandidat fuer einen
 * externen Block in Quadient Inspire.
 *
 * @param id           Nummer, z. B. {@code B001}
 * @param name         vorgeschlagener Blockname (gueltiger Inspire-Bezeichner)
 * @param areas        Bereiche, in denen der Block vorkommt
 * @param paragraphs   Text der Absaetze
 * @param letters      Umfang in Buchstaben und Ziffern
 * @param occurrences  Fundstellen
 * @param documents    Vorlagen, die den Block enthalten
 * @param fields       Platzhalter fuer Felder im Block, z. B. «Name»
 */
public record Block(String id,
                    String name,
                    Set<String> areas,
                    List<String> paragraphs,
                    int letters,
                    List<Occurrence> occurrences,
                    Set<String> documents,
                    Set<String> fields) {

    /**
     * Fundstelle eines Blocks.
     *
     * @param document  Vorlage
     * @param area      Bereich
     * @param from      erster Absatz (Nummer laut {@link Paragraph#number()})
     * @param to        letzter Absatz
     */
    public record Occurrence(String document, String area, int from, int to) {
    }

    /** Eingesparter Pflegeaufwand in Zeichen, wenn der Block nur einmal gepflegt wird. */
    public long reuseValue() {
        return (long) letters * (documents.size() - 1);
    }
}
