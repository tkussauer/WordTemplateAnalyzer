package com.github.tkussauer.wta.fonts;

import org.junit.jupiter.api.Test;

import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RunPropertiesTest {

    /** Word rechnet intern mit Ganzzahlen; Abweichungen von 1 je Kanal sind zulaessig. */
    private static void assertColor(String expected, String actual) {
        byte[] e = HexFormat.of().parseHex(expected);
        byte[] a = HexFormat.of().parseHex(actual);
        for (int i = 0; i < 3; i++) {
            assertTrue(Math.abs(Byte.toUnsignedInt(e[i]) - Byte.toUnsignedInt(a[i])) <= 1,
                    () -> "expected " + expected + " but was " + actual);
        }
    }

    private static String adjust(String hex, Integer shade, Integer tint) {
        HexFormat format = HexFormat.of().withUpperCase();
        return format.formatHex(RunProperties.adjustLuminance(format.parseHex(hex), shade, tint));
    }

    @Test
    void appliesThemeShadeLikeWord() {
        // Akzent 1 (Office 2007) mit themeShade="BF" = Ueberschrift 1
        assertColor("365F91", adjust("4F81BD", 0xBF, null));
    }

    @Test
    void appliesThemeTint() {
        assertEquals("FFFFFF", adjust("4F81BD", null, 0));
        assertColor("4F81BD", adjust("4F81BD", null, 0xFF));
    }

    @Test
    void keepsColorWithoutModifiers() {
        assertEquals("C00000", adjust("C00000", null, null));
    }
}
