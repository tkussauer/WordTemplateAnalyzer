package com.github.tkussauer.wta.datamapping;

import java.util.List;

/**
 * Alle Variablenvorkommen einer Vorlage.
 *
 * @param document     Dokumentname (relativ zum Eingabeverzeichnis)
 * @param format       Dateiformat, z. B. "docx"
 * @param occurrences  gefundene Vorkommen in Dokumentreihenfolge
 */
public record TemplateVariables(String document, String format, List<VariableOccurrence> occurrences) {
}
