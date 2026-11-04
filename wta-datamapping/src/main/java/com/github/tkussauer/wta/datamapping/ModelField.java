package com.github.tkussauer.wta.datamapping;

import java.util.List;
import java.util.Set;

/**
 * Ein Feld des vorgeschlagenen Inspire-Datenmodells, zusammengefuehrt aus allen gleichbedeutenden
 * Word-Variablen.
 *
 * @param group       Gruppe (Datenstruktur), ggf. verschachtelt mit Punkt, z. B. {@code Vertrag}
 * @param name        Feldname als gueltiger Inspire-Bezeichner
 * @param type        vorgeschlagener Inspire-Datentyp
 * @param formats     Word-Formate (z. B. {@code dd.MM.yyyy})
 * @param values      zulaessige Werte (Auswahllisten)
 * @param example     Beispiel- bzw. Standardwert
 * @param conditional {@code true}, wenn das Feld nur innerhalb von IF-Feldern verwendet wird
 * @param templates   Vorlagen, in denen das Feld vorkommt
 * @param sources     Herkunft in Word, z. B. {@code MERGEFIELD Name}
 * @param mergedCount Anzahl unterschiedlicher Word-Variablen, die in diesem Feld zusammengefuehrt wurden
 * @param notes       Hinweise zur Pruefung
 */
public record ModelField(String group,
                         String name,
                         InspireType type,
                         Set<String> formats,
                         Set<String> values,
                         String example,
                         boolean conditional,
                         Set<String> templates,
                         Set<String> sources,
                         int mergedCount,
                         List<String> notes) {

    /** Wurzel der Datenstruktur in Inspire. */
    public static final String ROOT = "Data";

    public String path() {
        return ROOT + "." + group + "." + name;
    }
}
