package com.github.tkussauer.wta.complexity;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Ermittelt die Komplexitaetskennzahlen eines Word-Dokuments.
 */
public interface MetricsExtractor {

    TemplateMetrics extract(Path file, String documentName) throws IOException;
}
