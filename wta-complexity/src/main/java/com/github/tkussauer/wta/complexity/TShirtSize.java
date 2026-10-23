package com.github.tkussauer.wta.complexity;

import java.util.Locale;

/**
 * Komplexitaetsklassen in T-Shirt-Groessen.
 */
public enum TShirtSize {

    S("einfach"),
    M("mittel"),
    L("komplex"),
    XL("sehr komplex");

    private final String description;

    TShirtSize(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }

    public TShirtSize max(TShirtSize other) {
        return other != null && other.ordinal() > ordinal() ? other : this;
    }

    public static TShirtSize parse(String value) {
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Ungültige T-Shirt-Größe '" + value + "' (erlaubt: S, M, L, XL)");
        }
    }
}
