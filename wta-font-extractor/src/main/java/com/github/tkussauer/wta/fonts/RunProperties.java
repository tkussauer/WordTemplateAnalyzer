package com.github.tkussauer.wta.fonts;

import org.apache.poi.ooxml.util.POIXMLUnits;
import org.apache.poi.xwpf.usermodel.XWPFTheme;
import org.openxmlformats.schemas.drawingml.x2006.main.CTColor;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTFonts;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTHpsMeasure;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTOnOff;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTUnderline;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STUnderline;

import java.math.BigInteger;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Veraenderliche Sammlung der fuer den Schriftstil relevanten Zeicheneigenschaften eines
 * OOXML-Dokuments. Ebenen (Dokumentstandard, Formatvorlagen, direkte Formatierung) werden
 * nacheinander mit {@link #apply(CTRPr)} ueberlagert.
 */
final class RunProperties {

    /** Word-Standard, wenn weder Dokumentstandard noch Formatvorlage eine Schrift festlegen. */
    static final String DEFAULT_FONT = "Times New Roman";
    static final double DEFAULT_SIZE_PT = 10.0;

    /** Zuordnung der WordprocessingML-Designfarben zu den Elementen des Farbschemas. */
    private static final Map<String, String> THEME_COLOR_ELEMENTS = Map.ofEntries(
            Map.entry("dark1", "dk1"), Map.entry("text1", "dk1"),
            Map.entry("light1", "lt1"), Map.entry("background1", "lt1"),
            Map.entry("dark2", "dk2"), Map.entry("text2", "dk2"),
            Map.entry("light2", "lt2"), Map.entry("background2", "lt2"),
            Map.entry("hyperlink", "hlink"), Map.entry("followedHyperlink", "folHlink"));

    private String font;
    private String themeFont;
    private Double sizePt;
    private Boolean bold;
    private Boolean italic;
    private Boolean underline;
    private String color;
    private String themeColor;
    /** Schattierung (themeShade) bzw. Aufhellung (themeTint) der Designfarbe, 0-255. */
    private Integer themeShade;
    private Integer themeTint;

    RunProperties copy() {
        RunProperties copy = new RunProperties();
        copy.font = font;
        copy.themeFont = themeFont;
        copy.sizePt = sizePt;
        copy.bold = bold;
        copy.italic = italic;
        copy.underline = underline;
        copy.color = color;
        copy.themeColor = themeColor;
        copy.themeShade = themeShade;
        copy.themeTint = themeTint;
        return copy;
    }

    /**
     * Ueberlagert die gesetzten Eigenschaften aus {@code rPr}.
     */
    RunProperties apply(CTRPr rPr) {
        if (rPr == null) {
            return this;
        }
        CTFonts fonts = last(rPr.getRFontsList());
        if (fonts != null) {
            applyFonts(fonts);
        }
        CTHpsMeasure sz = last(rPr.getSzList());
        if (sz != null) {
            Double parsed = parseHalfPoints(sz.getVal());
            if (parsed != null) {
                sizePt = parsed;
            }
        }
        CTOnOff b = last(rPr.getBList());
        if (b != null) {
            bold = POIXMLUnits.parseOnOff(b);
        }
        CTOnOff i = last(rPr.getIList());
        if (i != null) {
            italic = POIXMLUnits.parseOnOff(i);
        }
        CTUnderline u = last(rPr.getUList());
        if (u != null) {
            underline = !u.isSetVal() || u.getVal() != STUnderline.NONE;
        }
        org.openxmlformats.schemas.wordprocessingml.x2006.main.CTColor c = last(rPr.getColorList());
        if (c != null) {
            color = colorValue(c.getVal());
            themeColor = c.isSetThemeColor() ? c.getThemeColor().toString() : null;
            themeShade = c.isSetThemeShade() ? Byte.toUnsignedInt(c.getThemeShade()[0]) : null;
            themeTint = c.isSetThemeTint() ? Byte.toUnsignedInt(c.getThemeTint()[0]) : null;
        }
        return this;
    }

    /**
     * @return {@code true}, wenn {@code rPr} mindestens eine schriftrelevante Eigenschaft setzt
     */
    static boolean hasFontProperties(CTRPr rPr) {
        return rPr != null && (!rPr.getRFontsList().isEmpty() || !rPr.getSzList().isEmpty()
                || !rPr.getBList().isEmpty() || !rPr.getIList().isEmpty()
                || !rPr.getUList().isEmpty() || !rPr.getColorList().isEmpty());
    }

    FontStyle toFontStyle(XWPFTheme theme) {
        return new FontStyle(resolveFont(theme),
                sizePt != null ? sizePt : DEFAULT_SIZE_PT,
                Boolean.TRUE.equals(bold),
                Boolean.TRUE.equals(italic),
                Boolean.TRUE.equals(underline),
                resolveColor(theme));
    }

    private void applyFonts(CTFonts fonts) {
        // Designschrift hat innerhalb eines Elements Vorrang vor dem expliziten Namen
        if (fonts.isSetAsciiTheme()) {
            themeFont = fonts.getAsciiTheme().toString();
        } else if (fonts.isSetAscii()) {
            font = fonts.getAscii();
            themeFont = null;
        } else if (fonts.isSetHAnsiTheme()) {
            themeFont = fonts.getHAnsiTheme().toString();
        } else if (fonts.isSetHAnsi()) {
            font = fonts.getHAnsi();
            themeFont = null;
        }
    }

    private String resolveFont(XWPFTheme theme) {
        if (themeFont != null && theme != null) {
            String themed = themeFont.startsWith("major") ? theme.getMajorFont() : theme.getMinorFont();
            if (themed != null && !themed.isBlank()) {
                return themed;
            }
        }
        return font != null && !font.isBlank() ? font : DEFAULT_FONT;
    }

    private String resolveColor(XWPFTheme theme) {
        if (themeColor != null && theme != null) {
            byte[] rgb = themeColorRgb(theme, themeColor);
            if (rgb != null) {
                return HexFormat.of().withUpperCase().formatHex(adjustLuminance(rgb, themeShade, themeTint));
            }
        }
        if (color == null || color.equalsIgnoreCase("auto")) {
            return FontStyle.AUTO_COLOR;
        }
        return color.toUpperCase(Locale.ROOT);
    }

    private static byte[] themeColorRgb(XWPFTheme theme, String themeColor) {
        String element = THEME_COLOR_ELEMENTS.getOrDefault(themeColor, themeColor);
        CTColor ctColor;
        try {
            ctColor = theme.getCTColor(element);
        } catch (RuntimeException e) {
            return null;
        }
        if (ctColor == null) {
            return null;
        }
        byte[] rgb = null;
        if (ctColor.isSetSrgbClr()) {
            rgb = ctColor.getSrgbClr().getVal();
        } else if (ctColor.isSetSysClr() && ctColor.getSysClr().isSetLastClr()) {
            rgb = ctColor.getSysClr().getLastClr();
        }
        return rgb != null && rgb.length == 3 ? rgb : null;
    }

    /**
     * Wendet Schattierung/Aufhellung wie Word auf die Helligkeit (HSL) der Farbe an.
     * Durch Rundung kann das Ergebnis je Kanal um 1 von Word abweichen.
     */
    static byte[] adjustLuminance(byte[] rgb, Integer shade, Integer tint) {
        if (shade == null && tint == null) {
            return rgb;
        }
        double r = Byte.toUnsignedInt(rgb[0]) / 255.0;
        double g = Byte.toUnsignedInt(rgb[1]) / 255.0;
        double b = Byte.toUnsignedInt(rgb[2]) / 255.0;
        double max = Math.max(r, Math.max(g, b));
        double min = Math.min(r, Math.min(g, b));
        double l = (max + min) / 2;
        double h = 0;
        double s = 0;
        if (max != min) {
            double d = max - min;
            s = l > 0.5 ? d / (2 - max - min) : d / (max + min);
            if (max == r) {
                h = (g - b) / d + (g < b ? 6 : 0);
            } else if (max == g) {
                h = (b - r) / d + 2;
            } else {
                h = (r - g) / d + 4;
            }
            h /= 6;
        }
        if (shade != null) {
            l = l * shade / 255.0;
        }
        if (tint != null) {
            l = l * tint / 255.0 + (1 - tint / 255.0);
        }
        double q = l < 0.5 ? l * (1 + s) : l + s - l * s;
        double p = 2 * l - q;
        return new byte[] {
                toByte(s == 0 ? l : hueToRgb(p, q, h + 1.0 / 3)),
                toByte(s == 0 ? l : hueToRgb(p, q, h)),
                toByte(s == 0 ? l : hueToRgb(p, q, h - 1.0 / 3))};
    }

    private static double hueToRgb(double p, double q, double t) {
        if (t < 0) {
            t += 1;
        }
        if (t > 1) {
            t -= 1;
        }
        if (t < 1.0 / 6) {
            return p + (q - p) * 6 * t;
        }
        if (t < 1.0 / 2) {
            return q;
        }
        if (t < 2.0 / 3) {
            return p + (q - p) * (2.0 / 3 - t) * 6;
        }
        return p;
    }

    private static byte toByte(double value) {
        return (byte) Math.round(Math.max(0, Math.min(1, value)) * 255);
    }

    /** Der Farbwert ist entweder ein Hexwert (als Bytes) oder "auto". */
    private static String colorValue(Object value) {
        if (value instanceof byte[] rgb) {
            return HexFormat.of().withUpperCase().formatHex(rgb);
        }
        return value != null ? value.toString() : null;
    }

    /**
     * Groessen sind in OOXML halbe Punkte; die strikte Variante erlaubt auch Angaben wie "12pt".
     */
    private static Double parseHalfPoints(Object value) {
        if (value instanceof BigInteger halfPoints) {
            return halfPoints.doubleValue() / 2.0;
        }
        if (value instanceof Number halfPoints) {
            return halfPoints.doubleValue() / 2.0;
        }
        if (value != null) {
            String text = value.toString().trim();
            try {
                if (text.endsWith("pt")) {
                    return Double.parseDouble(text.substring(0, text.length() - 2));
                }
                return Double.parseDouble(text) / 2.0;
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private static <T> T last(List<T> list) {
        return list.isEmpty() ? null : list.get(list.size() - 1);
    }
}
