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
     * Liefert die zu analysierenden Vorlagen: die Datei selbst oder alle Word-Dateien im Verzeichnis.
     *
     * @param input  einzelne Word-Datei oder Verzeichnis
     */
    public static List<Path> resolve(Path input, boolean recursive) throws IOException {
        if (Files.isRegularFile(input)) {
            if (WordFormat.of(input).isEmpty()) {
                throw new IOException("Keine Word-Datei (docx, dotx, docm, dotm, doc, dot): " + input);
            }
            return List.of(input);
        }
        return findTemplates(input, recursive);
    }

    /**
     * Name einer Vorlage im Ergebnis: relativ zum Eingabeverzeichnis bzw. der Dateiname.
     */
    public static String displayName(Path input, Path file) {
        return Files.isRegularFile(input) ? file.getFileName().toString() : input.relativize(file).toString();
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
