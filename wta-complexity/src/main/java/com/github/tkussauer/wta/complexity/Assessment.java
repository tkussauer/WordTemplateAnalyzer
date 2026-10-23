package com.github.tkussauer.wta.complexity;

import java.util.List;
import java.util.Map;

/**
 * Bewertung einer Vorlage.
 *
 * @param metrics       gemessene Kennzahlen
 * @param points        Punkte je Kennzahl
 * @param totalPoints   Gesamtpunkte
 * @param size          T-Shirt-Groesse
 * @param sizeByPoints  Groesse allein nach Punkten
 * @param minimumRules  Kennzahlen, deren Mindestgroesse die Einstufung angehoben hat
 * @param drivers       Kennzahlen mit den meisten Punkten (absteigend)
 */
public record Assessment(TemplateMetrics metrics,
                         Map<Metric, Double> points,
                         double totalPoints,
                         TShirtSize size,
                         TShirtSize sizeByPoints,
                         List<Metric> minimumRules,
                         List<Metric> drivers) {
}
