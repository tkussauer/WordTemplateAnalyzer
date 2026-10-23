package com.github.tkussauer.wta.fonts;

/**
 * Verwendung eines Schriftstils im Text eines Dokuments (zusammengefasst je Dokument,
 * Bereich, Formatvorlagen und Schriftstil).
 *
 * @param area               Dokumentbereich (Hauptdokument, Kopfzeile, ...)
 * @param paragraphStyle     Name der Absatzformatvorlage
 * @param characterStyle     Name der Zeichenformatvorlage oder {@code null}
 * @param directFormatting   {@code true}, wenn der Text direkt (ohne Formatvorlage) formatiert ist
 * @param style              effektiver Schriftstil
 * @param runCount           Anzahl Textabschnitte (Runs) mit diesem Stil
 * @param characterCount     Anzahl Zeichen mit diesem Stil
 * @param sampleText         Textbeispiel
 */
public record FontUsage(String area,
                        String paragraphStyle,
                        String characterStyle,
                        boolean directFormatting,
                        FontStyle style,
                        int runCount,
                        int characterCount,
                        String sampleText) {
}
