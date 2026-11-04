package com.github.tkussauer.wta.datamapping;

import org.apache.poi.hpsf.CustomProperties;
import org.apache.poi.hpsf.DocumentSummaryInformation;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.usermodel.Bookmark;
import org.apache.poi.hwpf.usermodel.Bookmarks;
import org.apache.poi.hwpf.usermodel.Range;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Variablen aus binaeren Word-97-2003-Dokumenten (doc, dot).
 * <p>
 * Formularfelder erhalten ihren Namen ueber die gleichnamige Textmarke, die Word am Feldbeginn anlegt.
 * Dokumentvariablen werden im Binaerformat nicht ausgelesen.
 */
public class DocVariableExtractor implements VariableExtractor {

    private static final char FIELD_BEGIN = 0x13;
    private static final char FIELD_SEPARATOR = 0x14;
    private static final char FIELD_END = 0x15;
    private static final char PARAGRAPH_END = 0x0D;
    private static final char CELL_END = 0x07;
    private static final char LINE_BREAK = 0x0B;

    @Override
    public TemplateVariables extract(Path file, String documentName, List<Pattern> placeholders,
                                     boolean includeBookmarks) throws IOException {
        VariableCollector collector = new VariableCollector(documentName, placeholders, includeBookmarks);
        try (InputStream in = Files.newInputStream(file); HWPFDocument doc = new HWPFDocument(in)) {
            Map<Integer, List<String>> bookmarks = bookmarksByStart(doc.getBookmarks());
            scan(doc::getRange, "Hauptdokument", bookmarks, collector);
            scan(doc::getHeaderStoryRange, "Kopf-/Fußzeile", bookmarks, collector);
            scan(doc::getFootnoteRange, "Fußnote", bookmarks, collector);
            scan(doc::getEndnoteRange, "Endnote", bookmarks, collector);
            scan(doc::getMainTextboxRange, "Textfeld", bookmarks, collector);
            readCustomProperties(doc.getDocumentSummaryInformation(), collector);
        }
        return new TemplateVariables(documentName, VariableExtractor.extension(file), collector.finish());
    }

    private static void scan(Supplier<Range> rangeSupplier, String area, Map<Integer, List<String>> bookmarks,
                             VariableCollector collector) {
        Range range;
        try {
            range = rangeSupplier.get();
        } catch (RuntimeException e) {
            // Bereich in der Datei nicht vorhanden
            return;
        }
        if (range == null) {
            return;
        }
        collector.area(area);
        String text = range.text();
        int offset = range.getStartOffset();
        StringBuilder chunk = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            List<String> names = bookmarks.get(offset + i);
            if (c == FIELD_BEGIN || c == FIELD_SEPARATOR || c == FIELD_END || c == PARAGRAPH_END || c == CELL_END
                    || names != null) {
                flush(chunk, collector);
            }
            if (names != null && c != FIELD_BEGIN) {
                names.forEach(collector::bookmark);
            }
            switch (c) {
                case FIELD_BEGIN -> {
                    // Formularfeld: Word legt am Feldbeginn eine gleichnamige Textmarke an
                    String formName = names != null && !names.isEmpty() ? names.get(0) : null;
                    collector.beginField(formName != null ? new FormFieldInfo(formName, null, null, null, null) : null);
                    if (names != null) {
                        names.forEach(collector::bookmark);
                    }
                }
                case FIELD_SEPARATOR -> collector.separateField();
                case FIELD_END -> collector.endField();
                case PARAGRAPH_END, CELL_END -> collector.paragraphEnd();
                case LINE_BREAK, '\t' -> chunk.append(' ');
                default -> {
                    if (c >= 0x20) {
                        chunk.append(c);
                    }
                }
            }
        }
        flush(chunk, collector);
        collector.paragraphEnd();
    }

    /** Text gehoert je nach Zustand zum Feldcode oder zum sichtbaren Text. */
    private static void flush(StringBuilder chunk, VariableCollector collector) {
        if (!chunk.isEmpty()) {
            collector.fieldCode(chunk.toString());
            collector.text(chunk.toString());
            chunk.setLength(0);
        }
    }

    private static Map<Integer, List<String>> bookmarksByStart(Bookmarks bookmarks) {
        Map<Integer, List<String>> result = new HashMap<>();
        for (int i = 0; i < bookmarks.getBookmarksCount(); i++) {
            Bookmark bookmark = bookmarks.getBookmark(i);
            result.computeIfAbsent(bookmark.getStart(), k -> new ArrayList<>()).add(bookmark.getName());
        }
        return result;
    }

    private static void readCustomProperties(DocumentSummaryInformation info, VariableCollector collector) {
        if (info == null) {
            return;
        }
        CustomProperties properties = info.getCustomProperties();
        if (properties == null) {
            return;
        }
        for (String name : properties.nameSet()) {
            Object value = properties.get(name);
            collector.definition(VariableKind.DOC_PROPERTY, name, value != null ? value.toString() : null,
                    "Dokumenteigenschaften");
        }
    }
}
