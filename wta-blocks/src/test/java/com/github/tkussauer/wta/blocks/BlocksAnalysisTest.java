package com.github.tkussauer.wta.blocks;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlocksAnalysisTest {

    @TempDir
    Path tempDir;

    @Test
    void findsSharedBlocksAndSimilarTemplates() throws Exception {
        BlockTemplates.writeAll(tempDir);

        BlocksAnalysis.Result result = new BlocksAnalysis(BlocksAnalysis.Settings.DEFAULT)
                .analyze(tempDir, true, message -> { });

        assertEquals(3, result.templates().size());
        Block legal = find(result.blocks(), BlockTemplates.LEGAL.get(0), 3);
        // Vertragsnummer-Absatz + Rechtsblock: gleich in allen drei Vorlagen trotz anderer Feldergebnisse
        assertEquals(Set.of("a.docx", "b.docx", "c.docx"), legal.documents());
        assertEquals(4, legal.paragraphs().size());
        assertEquals("Ihre Vertragsnummer lautet «Vertragsnummer» und gilt für alle Rückfragen.",
                legal.paragraphs().get(0));
        assertEquals(Set.of("«Vertragsnummer»"), legal.fields());
        assertEquals(Set.of("Inhalt"), legal.areas());

        // Anrede, Einleitung + Rechtsblock nur in A und B: laengerer Block mit weniger Vorlagen
        Block intro = find(result.blocks(), BlockTemplates.INTRO, 2);
        assertEquals(Set.of("a.docx", "b.docx"), intro.documents());
        assertEquals(6, intro.paragraphs().size());
        assertEquals("Sehr geehrte Damen und Herren,", intro.paragraphs().get(0));

        Block footer = find(result.blocks(), BlockTemplates.FOOTER, 3);
        assertEquals(Set.of("Fußzeile"), footer.areas());
        assertTrue(footer.name().startsWith("Fuss_Musterfirma"), footer.name());
        assertEquals(3, result.blocks().size(), result.blocks().toString());

        // Nur A und B erreichen 80 %
        List<Similarity.Pair> pairs = result.similarity().pairs(0.8);
        assertEquals(1, pairs.size());
        Similarity.Pair pair = pairs.get(0);
        assertEquals(Set.of("a.docx", "b.docx"), Set.of(result.templates().get(pair.a()).document(),
                result.templates().get(pair.b()).document()));
        assertEquals(1, pair.onlyA());
        assertEquals(1, pair.onlyB());
        assertTrue(pair.previewA().startsWith("• "));
        assertEquals(1, result.similarity().groups(0.8).size());
    }

    @Test
    void respectsMinimumBlockSize() throws Exception {
        BlockTemplates.writeAll(tempDir);
        BlocksAnalysis.Result result = new BlocksAnalysis(new BlocksAnalysis.Settings(0.8, 15, 200, 3))
                .analyze(tempDir, true, message -> { });
        // Nur der Rechtsblock ist gross genug und in allen drei Vorlagen
        assertEquals(1, result.blocks().size());
        assertEquals(3, result.blocks().get(0).documents().size());
    }

    private static Block find(List<Block> blocks, String text, int documents) {
        return blocks.stream()
                .filter(b -> b.paragraphs().contains(text) && b.documents().size() == documents)
                .findFirst().orElseThrow(() -> new AssertionError(text + " nicht in " + blocks));
    }
}
