package com.github.tkussauer.wta.blocks;

import com.github.tkussauer.wta.common.DocxLayout;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ParagraphCollectorTest {

    @Test
    void replacesFieldsByPlaceholdersAndSkipsHiddenAreas() {
        ParagraphCollector collector = new ParagraphCollector();
        collector.area("Hauptdokument");
        collector.paragraphStart();
        collector.text("Hallo  ");
        collector.beginField();
        collector.fieldCode(" MERGEFIELD Name ");
        collector.separateField();
        collector.text("Max Muster");
        collector.endField();
        collector.text(", heute ist ");
        collector.beginField();
        collector.fieldCode(" IF ");
        collector.beginField();
        collector.fieldCode(" MERGEFIELD Tag ");
        collector.endField();
        collector.fieldCode(" = \"1\" \"Montag\" \"\" \\* MERGEFORMAT");
        collector.separateField();
        collector.text("Montag");
        collector.endField();
        collector.paragraphEnd();
        collector.paragraphStart();
        collector.text("  \uF0B7 ");
        collector.paragraphEnd();
        collector.area("Kopfzeile" + DocxLayout.NOT_DISPLAYED);
        collector.paragraphStart();
        collector.text("Versteckt");
        collector.paragraphEnd();
        collector.area("Fußzeile");
        collector.paragraphStart();
        collector.text("Fuß");
        collector.paragraphEnd();

        List<Paragraph> paragraphs = collector.finish();

        assertEquals(2, paragraphs.size());
        assertEquals("Hallo «Name», heute ist {IF {MERGEFIELD Tag} = \"1\" \"Montag\" \"\"}", paragraphs.get(0).text());
        assertEquals("Inhalt", paragraphs.get(0).area());
        assertEquals(new Paragraph(2, "Fußzeile", "Fuß"), paragraphs.get(1));
    }

    @Test
    void measuresSimilarityWithWordShingles() {
        TemplateText a = new TemplateText("a", "docx",
                List.of(new Paragraph(1, "Inhalt", "eins zwei drei vier fünf")));
        TemplateText b = new TemplateText("b", "docx",
                List.of(new Paragraph(1, "Inhalt", "eins zwei drei vier sechs")));
        // Shingles: {eins zwei drei, zwei drei vier, drei vier fünf} vs. {..., drei vier sechs}: 2 von 3 gleich
        assertEquals(2.0 / 3, Similarity.dice(Similarity.shingles(a), Similarity.shingles(b)), 1e-9);
        assertEquals(Set.of("eins", "zwei"), Similarity.shingles(new TemplateText("c", "docx",
                List.of(new Paragraph(1, "Inhalt", "Eins zwei")))));
    }
}
