package com.github.tkussauer.wta.datamapping;

import com.github.tkussauer.wta.common.ExtractionFailure;
import com.github.tkussauer.wta.common.TemplateScanner;
import org.apache.poi.poifs.filesystem.FileMagic;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Extrahiert die Variablen aller Word-Vorlagen eines Verzeichnisses.
 */
public class DataMappingAnalysis {

    /**
     * Standardmuster fuer Platzhalter im Fliesstext: «Name», {{Name}}, ${Name}, &lt;&lt;Name&gt;&gt;, [[Name]].
     * Gruppe 1 ist jeweils der Name.
     */
    public static final List<Pattern> DEFAULT_PLACEHOLDERS = List.of(
            Pattern.compile("«([^«»\\r\\n]{1,80})»"),
            Pattern.compile("\\{\\{\\s*([^{}\\r\\n]{1,80}?)\\s*}}"),
            Pattern.compile("\\$\\{([^{}\\r\\n]{1,80})}"),
            Pattern.compile("<<([^<>\\r\\n]{1,80})>>"),
            Pattern.compile("\\[\\[([^\\[\\]\\r\\n]{1,80})]]"));

    /**
     * Gesamtergebnis einer Analyse.
     */
    public record Result(List<TemplateVariables> templates, List<ExtractionFailure> failures) {
    }

    private final List<Pattern> placeholders;
    private final boolean includeBookmarks;
    private final VariableExtractor docxExtractor = new DocxVariableExtractor();
    private final VariableExtractor docExtractor = new DocVariableExtractor();

    public DataMappingAnalysis(List<Pattern> placeholders, boolean includeBookmarks) {
        this.placeholders = List.copyOf(placeholders);
        this.includeBookmarks = includeBookmarks;
    }

    /**
     * @param directory  Verzeichnis mit Word-Vorlagen
     * @param recursive  Unterverzeichnisse einbeziehen
     * @param progress   erhaelt je Dokument eine Fortschrittsmeldung
     */
    public Result analyze(Path directory, boolean recursive, Consumer<String> progress) throws IOException {
        List<Path> files = TemplateScanner.findTemplates(directory, recursive);
        List<TemplateVariables> templates = new ArrayList<>();
        List<ExtractionFailure> failures = new ArrayList<>();
        int index = 0;
        for (Path file : files) {
            String name = directory.relativize(file).toString();
            try {
                TemplateVariables variables = extractorFor(file).extract(file, name, placeholders, includeBookmarks);
                templates.add(variables);
                long distinct = variables.occurrences().stream().map(VariableOccurrence::key).distinct().count();
                progress.accept(String.format("[%d/%d] %s: %d Variable(n)", ++index, files.size(), name, distinct));
            } catch (Exception | LinkageError e) {
                String message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                failures.add(new ExtractionFailure(name, message));
                progress.accept(String.format("[%d/%d] FEHLER %s: %s", ++index, files.size(), name, message));
            }
        }
        return new Result(templates, failures);
    }

    /** Waehlt den Extraktor nach dem tatsaechlichen Dateiinhalt, nicht nach der Endung. */
    private VariableExtractor extractorFor(Path file) throws IOException {
        FileMagic magic = FileMagic.valueOf(file.toFile());
        return switch (magic) {
            case OOXML -> docxExtractor;
            case OLE2 -> docExtractor;
            default -> throw new IOException("Kein unterstuetztes Word-Format (erkannt: " + magic + ")");
        };
    }
}
