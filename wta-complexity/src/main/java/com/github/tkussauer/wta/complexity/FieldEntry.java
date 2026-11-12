package com.github.tkussauer.wta.complexity;

/**
 * Ein Datenfeld oder eine Bedingung einer Vorlage, zusammengefasst je Bereich.
 *
 * @param region       Inhalt oder Kopf-/Fusszeile
 * @param condition    {@code true} fuer Bedingungen, {@code false} fuer Datenfelder
 * @param keyword      Feldart, z. B. MERGEFIELD oder IF
 * @param name         Feldname bzw. Feldcode der Bedingung (verschachtelte Felder in {...})
 * @param occurrences  Anzahl der Vorkommen im Bereich
 */
public record FieldEntry(Region region, boolean condition, String keyword, String name, int occurrences) {
}
