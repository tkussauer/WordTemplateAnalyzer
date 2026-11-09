package com.github.tkussauer.wta.conditions;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Liest die Felder eines Word-Dokuments als Baum.
 */
interface FieldReader {

    /** @return Felder auf oberster Ebene in Dokumentreihenfolge */
    List<FieldNode> read(Path file) throws IOException;

    static String extension(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
    }
}
