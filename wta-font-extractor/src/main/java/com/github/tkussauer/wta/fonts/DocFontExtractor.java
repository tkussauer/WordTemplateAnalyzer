package com.github.tkussauer.wta.fonts;

import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.model.StyleDescription;
import org.apache.poi.hwpf.model.StyleSheet;
import org.apache.poi.hwpf.usermodel.CharacterProperties;
import org.apache.poi.hwpf.usermodel.CharacterRun;
import org.apache.poi.hwpf.usermodel.Paragraph;
import org.apache.poi.hwpf.usermodel.Range;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Schriftanalyse fuer binaere Word-97-2003-Dokumente (doc, dot).
 */
public class DocFontExtractor implements FontExtractor {

    private static final char FIELD_BEGIN = 0x13;
    private static final char FIELD_SEPARATOR = 0x14;
    private static final char FIELD_END = 0x15;

    @Override
    public DocumentFonts extract(Path file, String documentName) throws IOException {
        try (InputStream in = Files.newInputStream(file); HWPFDocument doc = new HWPFDocument(in)) {
            return new Analysis(doc).run(documentName);
        }
    }

    private static final class Analysis {

        private final HWPFDocument doc;
        private final StyleSheet styleSheet;
        private final UsageCollector usages = new UsageCollector();
        private final Set<Integer> usedStyles = new HashSet<>();
        /** Offene Felder; {@code true}, solange der Feldcode (nicht das Ergebnis) gelesen wird. */
        private final Deque<Boolean> openFields = new ArrayDeque<>();

        Analysis(HWPFDocument doc) {
            this.doc = doc;
            this.styleSheet = doc.getStyleSheet();
        }

        DocumentFonts run(String documentName) {
            scan(doc::getRange, "Hauptdokument");
            scan(doc::getHeaderStoryRange, "Kopf-/Fußzeile");
            scan(doc::getFootnoteRange, "Fußnote");
            scan(doc::getEndnoteRange, "Endnote");
            scan(doc::getMainTextboxRange, "Textfeld");
            return new DocumentFonts(documentName, usages.toUsages(), styleDefinitions());
        }

        private void scan(Supplier<Range> rangeSupplier, String area) {
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
            openFields.clear();
            for (int p = 0; p < range.numParagraphs(); p++) {
                Paragraph paragraph = range.getParagraph(p);
                int styleIndex = paragraph.getStyleIndex();
                usedStyles.add(styleIndex);
                String paragraphStyle = styleName(styleIndex);
                for (int r = 0; r < paragraph.numCharacterRuns(); r++) {
                    CharacterRun run = paragraph.getCharacterRun(r);
                    String text = visibleText(run.text());
                    if (text.isBlank()) {
                        continue;
                    }
                    usages.add(area, paragraphStyle, null, false, toFontStyle(run), text);
                }
            }
        }

        /** Entfernt Steuerzeichen und Feldfunktionscodes, behaelt Feldergebnisse. */
        private String visibleText(String text) {
            StringBuilder visible = new StringBuilder(text.length());
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c == FIELD_BEGIN) {
                    openFields.push(Boolean.TRUE);
                } else if (c == FIELD_SEPARATOR) {
                    if (!openFields.isEmpty()) {
                        openFields.pop();
                        openFields.push(Boolean.FALSE);
                    }
                } else if (c == FIELD_END) {
                    openFields.poll();
                } else if (!openFields.contains(Boolean.TRUE) && (c >= 0x20 || c == '\t')) {
                    visible.append(c);
                }
            }
            return visible.toString();
        }

        private FontStyle toFontStyle(CharacterRun run) {
            String font = run.getFontName();
            return new FontStyle(
                    font != null && !font.isBlank() ? font : RunProperties.DEFAULT_FONT,
                    run.getFontSize() / 2.0,
                    run.isBold(),
                    run.isItalic(),
                    run.getUnderlineCode() != 0,
                    color(run.getIco24()));
        }

        private List<StyleDefinition> styleDefinitions() {
            List<StyleDefinition> result = new ArrayList<>();
            for (int i = 0; i < styleSheet.numStyles(); i++) {
                StyleDescription description = styleSheet.getStyleDescription(i);
                if (description == null || description.getName() == null) {
                    continue;
                }
                CharacterProperties chp = styleSheet.getCharacterStyle(i);
                if (chp == null) {
                    continue;
                }
                boolean paragraphStyle = description.getPAPX() != null;
                int base = description.getBaseStyle();
                String basedOn = base != StyleSheet.NIL_STYLE && base < styleSheet.numStyles() ? styleName(base) : null;
                result.add(new StyleDefinition(String.valueOf(i), description.getName(),
                        paragraphStyle ? "Absatz" : "Zeichen", basedOn, toFontStyle(chp), usedStyles.contains(i)));
            }
            return result;
        }

        private FontStyle toFontStyle(CharacterProperties chp) {
            String font = null;
            try {
                font = doc.getFontTable().getMainFont(chp.getFtcAscii());
            } catch (RuntimeException e) {
                // ungueltiger Index in der Schriftartentabelle
            }
            return new FontStyle(
                    font != null && !font.isBlank() ? font : RunProperties.DEFAULT_FONT,
                    chp.getHps() / 2.0,
                    chp.isFBold(),
                    chp.isFItalic(),
                    chp.getKul() != 0,
                    color(chp.getIco24()));
        }

        private String styleName(int index) {
            StyleDescription description = index >= 0 && index < styleSheet.numStyles()
                    ? styleSheet.getStyleDescription(index) : null;
            return description != null && description.getName() != null ? description.getName() : "Formatvorlage " + index;
        }

        /** Wandelt den HWPF-Farbwert (0xBBGGRR, -1 = automatisch) in RRGGBB um. */
        private static String color(int ico24) {
            if (ico24 == -1) {
                return FontStyle.AUTO_COLOR;
            }
            int r = ico24 & 0xFF;
            int g = (ico24 >> 8) & 0xFF;
            int b = (ico24 >> 16) & 0xFF;
            return String.format("%02X%02X%02X", r, g, b);
        }
    }
}
