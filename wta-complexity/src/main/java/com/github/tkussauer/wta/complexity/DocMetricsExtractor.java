package com.github.tkussauer.wta.complexity;

import com.github.tkussauer.wta.fonts.DocFontExtractor;
import org.apache.poi.hpsf.SummaryInformation;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.usermodel.Bookmarks;
import org.apache.poi.hwpf.usermodel.HeaderStories;
import org.apache.poi.hwpf.usermodel.OfficeDrawing;
import org.apache.poi.hwpf.usermodel.OfficeDrawings;
import org.apache.poi.hwpf.usermodel.Paragraph;
import org.apache.poi.hwpf.usermodel.Range;
import org.apache.poi.poifs.filesystem.DirectoryEntry;
import org.apache.poi.poifs.filesystem.Entry;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Komplexitaetskennzahlen fuer binaere Word-97-2003-Dokumente (doc, dot).
 */
public class DocMetricsExtractor implements MetricsExtractor {

    private static final char FIELD_BEGIN = 0x13;
    private static final char FIELD_SEPARATOR = 0x14;
    private static final char FIELD_END = 0x15;
    private static final char PAGE_BREAK = 0x0C;

    private final DocFontExtractor fontExtractor = new DocFontExtractor();

    @Override
    public TemplateMetrics extract(Path file, String documentName) throws IOException {
        TemplateMetrics metrics = new TemplateMetrics(documentName, DocxMetricsExtractor.extension(file));
        try (InputStream in = Files.newInputStream(file); HWPFDocument doc = new HWPFDocument(in)) {
            Range main = doc.getRange();
            Range overall = doc.getOverallRange();
            metrics.set(Metric.SECTIONS, main.numSections());
            countParagraphs(overall, metrics);
            // Kopf-/Fusszeilen- und Notenbereiche enthalten leere Platzhalterabsaetze
            metrics.set(Metric.PARAGRAPHS, main.numParagraphs() + nonBlankParagraphs(doc::getHeaderStoryRange)
                    + nonBlankParagraphs(doc::getFootnoteRange) + nonBlankParagraphs(doc::getEndnoteRange)
                    + nonBlankParagraphs(doc::getMainTextboxRange));
            countFields(overall.text(), metrics);

            int pageBreaks = (int) main.text().chars().filter(c -> c == PAGE_BREAK).count();
            for (int i = 0; i < main.numParagraphs(); i++) {
                if (main.getParagraph(i).pageBreakBefore()) {
                    pageBreaks++;
                }
            }
            int pages = 0;
            SummaryInformation summary = doc.getSummaryInformation();
            if (summary != null) {
                pages = summary.getPageCount();
            }
            metrics.set(Metric.PAGES, Math.max(pages, pageBreaks + 1));

            metrics.set(Metric.IMAGES, doc.getPicturesTable().getAllPictures().size());
            metrics.set(Metric.TEXT_BOXES, shapes(doc::getOfficeDrawingsMain) + shapes(doc::getOfficeDrawingsHeaders));
            metrics.set(Metric.HEADERS_FOOTERS, headersFooters(doc));
            metrics.set(Metric.NOTES, doc.getFootnotes().getNotesCount() + doc.getEndnotes().getNotesCount());
            metrics.set(Metric.BOOKMARKS, bookmarks(doc.getBookmarks()));

            DirectoryEntry root = doc.getDirectory();
            metrics.set(Metric.MACROS, root.hasEntry("Macros") ? 1 : 0);
            metrics.set(Metric.EMBEDDED_OBJECTS, embeddedObjects(root));
        }
        FontMetrics.apply(fontExtractor.extract(file, documentName), metrics);
        return metrics;
    }

    private static void countParagraphs(Range range, TemplateMetrics metrics) {
        Set<Integer> lists = new HashSet<>();
        int previousLevel = 0;
        for (int i = 0; i < range.numParagraphs(); i++) {
            Paragraph paragraph = range.getParagraph(i);
            int level = paragraph.isInTable() ? Math.max(1, paragraph.getTableLevel()) : 0;
            if (level > previousLevel) {
                metrics.add(Metric.TABLES, level - previousLevel);
                if (level > 1) {
                    metrics.add(Metric.NESTED_TABLES, level - Math.max(1, previousLevel));
                }
            }
            previousLevel = level;
            if (paragraph.isInList()) {
                lists.add(paragraph.getIlfo());
            }
        }
        metrics.set(Metric.LISTS, lists.size());
    }

    private static int nonBlankParagraphs(Supplier<Range> range) {
        try {
            Range r = range.get();
            int count = 0;
            for (int i = 0; i < r.numParagraphs(); i++) {
                if (!isBlank(r.getParagraph(i).text())) {
                    count++;
                }
            }
            return count;
        } catch (RuntimeException e) {
            return 0;
        }
    }

    private static boolean isBlank(String text) {
        return text == null || text.replaceAll("[\\p{Cntrl}\\s]", "").isEmpty();
    }

    static void countFields(String text, TemplateMetrics metrics) {
        FieldCounter fields = new FieldCounter(metrics);
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == FIELD_BEGIN || c == FIELD_SEPARATOR || c == FIELD_END) {
                fields.code(code.toString());
                code.setLength(0);
                if (c == FIELD_BEGIN) {
                    fields.begin();
                } else if (c == FIELD_SEPARATOR) {
                    fields.separate();
                } else {
                    fields.end();
                }
            } else {
                code.append(c);
            }
        }
        fields.finish();
    }

    private static int shapes(Supplier<OfficeDrawings> drawings) {
        try {
            int count = 0;
            for (OfficeDrawing drawing : drawings.get().getOfficeDrawings()) {
                // Frei positionierte Bilder zaehlen bereits als Bild
                if (drawing.getPictureData() == null) {
                    count++;
                }
            }
            return count;
        } catch (RuntimeException e) {
            return 0;
        }
    }

    private static int headersFooters(HWPFDocument doc) {
        HeaderStories stories;
        try {
            stories = new HeaderStories(doc);
        } catch (RuntimeException e) {
            return 0;
        }
        // Ist keine eigene erste/gerade Kopfzeile definiert, liefert Word die ungerade erneut
        Set<String> headers = new HashSet<>();
        Set<String> footers = new HashSet<>();
        for (String text : List.of(nullToEmpty(stories.getFirstHeader()), nullToEmpty(stories.getOddHeader()),
                nullToEmpty(stories.getEvenHeader()))) {
            if (!isBlank(text)) {
                headers.add(text.strip());
            }
        }
        for (String text : List.of(nullToEmpty(stories.getFirstFooter()), nullToEmpty(stories.getOddFooter()),
                nullToEmpty(stories.getEvenFooter()))) {
            if (!isBlank(text)) {
                footers.add(text.strip());
            }
        }
        return headers.size() + footers.size();
    }

    private static String nullToEmpty(String text) {
        return text != null ? text : "";
    }

    private static int bookmarks(Bookmarks bookmarks) {
        int count = 0;
        for (int i = 0; i < bookmarks.getBookmarksCount(); i++) {
            String name = bookmarks.getBookmark(i).getName();
            if (name != null && !name.isEmpty() && !name.startsWith("_")) {
                count++;
            }
        }
        return count;
    }

    private static int embeddedObjects(DirectoryEntry root) {
        try {
            Entry pool = root.getEntry("ObjectPool");
            return pool instanceof DirectoryEntry dir ? dir.getEntryCount() : 0;
        } catch (IOException | RuntimeException e) {
            return 0;
        }
    }
}
