package com.github.tkussauer.wta.blocks;

import com.github.tkussauer.wta.common.ExtractionFailure;
import com.github.tkussauer.wta.common.TemplateScanner;
import com.github.tkussauer.wta.common.WordDocumentReader;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Findet gleiche Bloecke und aehnliche Vorlagen in einem Verzeichnis.
 */
public class BlocksAnalysis {

    /**
     * Einstellungen.
     *
     * @param threshold            Aehnlichkeitsschwelle von 0 bis 1 (z. B. 0.8)
     * @param minParagraphLetters  Absaetze mit weniger Buchstaben werden beim Blockvergleich uebergangen
     * @param minBlockLetters      Mindestumfang eines Blocks in Buchstaben
     * @param minDocuments         Mindestanzahl Vorlagen je Block
     */
    public record Settings(double threshold, int minParagraphLetters, int minBlockLetters, int minDocuments) {

        public static final Settings DEFAULT = new Settings(0.8, 15, 50, 2);
    }

    /**
     * Gesamtergebnis.
     */
    public record Result(Settings settings, List<TemplateText> templates, List<Block> blocks, Similarity similarity,
                         List<ExtractionFailure> failures) {
    }

    private final Settings settings;

    public BlocksAnalysis(Settings settings) {
        this.settings = settings;
    }

    public Result analyze(Path input, boolean recursive, Consumer<String> progress) throws IOException {
        List<Path> files = TemplateScanner.resolve(input, recursive);
        List<TemplateText> templates = new ArrayList<>();
        List<ExtractionFailure> failures = new ArrayList<>();
        int index = 0;
        for (Path file : files) {
            String name = TemplateScanner.displayName(input, file);
            try {
                ParagraphCollector collector = new ParagraphCollector();
                WordDocumentReader.read(file, collector);
                List<Paragraph> paragraphs = collector.finish();
                templates.add(new TemplateText(name, WordDocumentReader.extension(file), paragraphs));
                progress.accept(String.format("[%d/%d] %s: %d Absätze", ++index, files.size(), name,
                        paragraphs.size()));
            } catch (Exception | LinkageError e) {
                String message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                failures.add(new ExtractionFailure(name, message));
                progress.accept(String.format("[%d/%d] FEHLER %s: %s", ++index, files.size(), name, message));
            }
        }
        progress.accept("Suche gleiche Blöcke ...");
        List<Block> blocks = new BlockFinder(settings.minParagraphLetters(), settings.minBlockLetters(),
                settings.minDocuments()).find(templates);
        progress.accept("Vergleiche Vorlagen ...");
        Similarity similarity = new Similarity(templates);
        return new Result(settings, templates, blocks, similarity, failures);
    }
}
