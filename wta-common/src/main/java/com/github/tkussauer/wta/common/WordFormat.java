package com.github.tkussauer.wta.common;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

/**
 * Von den Analyse-Anwendungen unterstuetzte Word-Dateiformate.
 */
public enum WordFormat {

    /** Office Open XML (docx, dotx, docm, dotm). */
    OOXML("docx", "dotx", "docm", "dotm"),

    /** Binaeres Word-97-2003-Format (doc, dot). */
    BINARY("doc", "dot");

    private final String[] extensions;

    WordFormat(String... extensions) {
        this.extensions = extensions;
    }

    /**
     * Ermittelt das Format anhand der Dateiendung.
     */
    public static Optional<WordFormat> of(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        if (dot < 0) {
            return Optional.empty();
        }
        String ext = name.substring(dot + 1);
        for (WordFormat format : values()) {
            for (String candidate : format.extensions) {
                if (candidate.equals(ext)) {
                    return Optional.of(format);
                }
            }
        }
        return Optional.empty();
    }
}
