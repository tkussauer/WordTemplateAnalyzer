package com.github.tkussauer.wta.complexity;

import com.github.tkussauer.wta.common.TemplateScanner;
import com.github.tkussauer.wta.fonts.ExtractionFailure;
import org.apache.poi.poifs.filesystem.FileMagic;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Bewertet alle Word-Vorlagen eines Verzeichnisses.
 */
public class ComplexityAnalysis {

    /**
     * Gesamtergebnis einer Analyse.
     */
    public record Result(ComplexityModel model, List<Assessment> assessments, List<ExtractionFailure> failures) {
    }

    private final ComplexityModel model;
    private final MetricsExtractor docxExtractor = new DocxMetricsExtractor();
    private final MetricsExtractor docExtractor = new DocMetricsExtractor();

    public ComplexityAnalysis(ComplexityModel model) {
        this.model = model;
    }

    /**
     * @param directory  Verzeichnis mit Word-Vorlagen
     * @param recursive  Unterverzeichnisse einbeziehen
     * @param progress   erhaelt je Dokument eine Fortschrittsmeldung
     */
    public Result analyze(Path directory, boolean recursive, Consumer<String> progress) throws IOException {
        List<Path> files = TemplateScanner.findTemplates(directory, recursive);
        List<Assessment> assessments = new ArrayList<>();
        List<ExtractionFailure> failures = new ArrayList<>();
        int index = 0;
        for (Path file : files) {
            String name = directory.relativize(file).toString();
            try {
                Assessment assessment = model.assess(extractorFor(file).extract(file, name));
                assessments.add(assessment);
                progress.accept(String.format("[%d/%d] %-3s %s", ++index, files.size(), assessment.size(), name));
            } catch (Exception | LinkageError e) {
                String message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                failures.add(new ExtractionFailure(name, message));
                progress.accept(String.format("[%d/%d] FEHLER %s: %s", ++index, files.size(), name, message));
            }
        }
        return new Result(model, assessments, failures);
    }

    /** Waehlt den Extraktor nach dem tatsaechlichen Dateiinhalt, nicht nach der Endung. */
    private MetricsExtractor extractorFor(Path file) throws IOException {
        FileMagic magic = FileMagic.valueOf(file.toFile());
        return switch (magic) {
            case OOXML -> docxExtractor;
            case OLE2 -> docExtractor;
            default -> throw new IOException("Kein unterstuetztes Word-Format (erkannt: " + magic + ")");
        };
    }
}
