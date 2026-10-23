package com.github.tkussauer.wta.common;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Sucht Word-Vorlagen in einem Verzeichnis.
 */
public final class TemplateScanner {

    private TemplateScanner() {
    }

    /**
     * Liefert alle Word-Dateien im Verzeichnis, sortiert nach Pfad.
     * Temporaere Sperrdateien von Word ("~$...") werden ignoriert.
     *
     * @param directory  zu durchsuchendes Verzeichnis
     * @param recursive  {@code true}, um auch Unterverzeichnisse zu durchsuchen
     */
    public static List<Path> findTemplates(Path directory, boolean recursive) throws IOException {
        if (!Files.isDirectory(directory)) {
            throw new IOException("Kein Verzeichnis: " + directory);
        }
        int depth = recursive ? Integer.MAX_VALUE : 1;
        try (Stream<Path> files = Files.walk(directory, depth)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(p -> !p.getFileName().toString().startsWith("~$"))
                    .filter(p -> WordFormat.of(p).isPresent())
                    .sorted(Comparator.comparing(p -> p.toString().toLowerCase()))
                    .toList();
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }
}
