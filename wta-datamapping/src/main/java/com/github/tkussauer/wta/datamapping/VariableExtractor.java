package com.github.tkussauer.wta.datamapping;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Extrahiert die Variablen eines Word-Dokuments.
 */
public interface VariableExtractor {

    /**
     * @param placeholders      Muster fuer Platzhalter im Text; Gruppe 1 ist der Name
     * @param includeBookmarks  Textmarken als Variablen aufnehmen
     */
    TemplateVariables extract(Path file, String documentName, List<Pattern> placeholders, boolean includeBookmarks)
            throws IOException;

    static String extension(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
    }
}
