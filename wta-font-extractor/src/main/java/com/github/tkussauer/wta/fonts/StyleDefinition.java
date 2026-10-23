package com.github.tkussauer.wta.fonts;

/**
 * Eine in der Vorlage definierte Formatvorlage mit ihrem effektiven Schriftstil.
 *
 * @param styleId   interne ID der Formatvorlage
 * @param name      angezeigter Name
 * @param type      Typ (Absatz, Zeichen, ...)
 * @param basedOn   Name der Basisformatvorlage oder {@code null}
 * @param style     effektiver Schriftstil (inkl. Vererbung)
 * @param used      {@code true}, wenn die Formatvorlage im Text verwendet wird
 */
public record StyleDefinition(String styleId, String name, String type, String basedOn, FontStyle style, boolean used) {
}
