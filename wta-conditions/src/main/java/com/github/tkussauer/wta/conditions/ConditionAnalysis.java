package com.github.tkussauer.wta.conditions;

import com.github.tkussauer.wta.common.ExtractionFailure;
import com.github.tkussauer.wta.common.TemplateScanner;
import org.apache.poi.poifs.filesystem.FileMagic;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Extrahiert die Bedingungen aus einer Vorlage oder allen Vorlagen eines Verzeichnisses.
 */
public class ConditionAnalysis {

    /**
     * Bedingungen einer Vorlage.
     */
    public record TemplateConditions(String document, String format, List<Condition> conditions) {
    }

    /**
     * Gesamtergebnis einer Analyse.
     */
    public record Result(List<TemplateConditions> templates, List<ExtractionFailure> failures) {
    }

    private final FieldReader docxReader = new DocxFieldReader();
    private final FieldReader docReader = new DocFieldReader();

    /**
     * @param input      Word-Datei oder Verzeichnis
     * @param recursive  Unterverzeichnisse einbeziehen
     * @param progress   erhaelt je Dokument eine Fortschrittsmeldung
     */
    public Result analyze(Path input, boolean recursive, Consumer<String> progress) throws IOException {
        List<Path> files = TemplateScanner.resolve(input, recursive);
        List<TemplateConditions> templates = new ArrayList<>();
        List<ExtractionFailure> failures = new ArrayList<>();
        int index = 0;
        for (Path file : files) {
            String name = TemplateScanner.displayName(input, file);
            try {
                List<Condition> conditions = ConditionParser.parse(name, readerFor(file).read(file));
                templates.add(new TemplateConditions(name, FieldReader.extension(file), conditions));
                progress.accept(String.format("[%d/%d] %s: %d Bedingung(en)", ++index, files.size(), name,
                        conditions.size()));
            } catch (Exception | LinkageError e) {
                String message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                failures.add(new ExtractionFailure(name, message));
                progress.accept(String.format("[%d/%d] FEHLER %s: %s", ++index, files.size(), name, message));
            }
        }
        return new Result(templates, failures);
    }

    /** Waehlt den Leser nach dem tatsaechlichen Dateiinhalt, nicht nach der Endung. */
    private FieldReader readerFor(Path file) throws IOException {
        FileMagic magic = FileMagic.valueOf(file.toFile());
        return switch (magic) {
            case OOXML -> docxReader;
            case OLE2 -> docReader;
            default -> throw new IOException("Kein unterstuetztes Word-Format (erkannt: " + magic + ")");
        };
    }
}
