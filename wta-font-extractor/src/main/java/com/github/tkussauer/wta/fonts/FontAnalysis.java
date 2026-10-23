package com.github.tkussauer.wta.fonts;

import com.github.tkussauer.wta.common.TemplateScanner;
import org.apache.poi.poifs.filesystem.FileMagic;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Analysiert alle Word-Vorlagen eines Verzeichnisses.
 */
public class FontAnalysis {

    /**
     * Gesamtergebnis einer Analyse.
     */
    public record Result(List<DocumentFonts> documents, List<ExtractionFailure> failures) {
    }

    private final FontExtractor docxExtractor = new DocxFontExtractor();
    private final FontExtractor docExtractor = new DocFontExtractor();

    /**
     * @param directory  Verzeichnis mit Word-Vorlagen
     * @param recursive  Unterverzeichnisse einbeziehen
     * @param progress   erhaelt je Dokument eine Fortschrittsmeldung
     */
    public Result analyze(Path directory, boolean recursive, Consumer<String> progress) throws IOException {
        List<Path> files = TemplateScanner.findTemplates(directory, recursive);
        List<DocumentFonts> documents = new ArrayList<>();
        List<ExtractionFailure> failures = new ArrayList<>();
        int index = 0;
        for (Path file : files) {
            String name = directory.relativize(file).toString();
            progress.accept(String.format("[%d/%d] %s", ++index, files.size(), name));
            try {
                documents.add(extractorFor(file).extract(file, name));
            } catch (Exception | LinkageError e) {
                String message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                failures.add(new ExtractionFailure(name, message));
                progress.accept("    FEHLER: " + message);
            }
        }
        return new Result(documents, failures);
    }

    /** Waehlt den Extraktor nach dem tatsaechlichen Dateiinhalt, nicht nach der Endung. */
    private FontExtractor extractorFor(Path file) throws IOException {
        FileMagic magic = FileMagic.valueOf(file.toFile());
        return switch (magic) {
            case OOXML -> docxExtractor;
            case OLE2 -> docExtractor;
            default -> throw new IOException("Kein unterstuetztes Word-Format (erkannt: " + magic + ")");
        };
    }
}
