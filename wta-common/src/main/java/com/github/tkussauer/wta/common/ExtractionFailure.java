package com.github.tkussauer.wta.common;

/**
 * Ein Dokument, das nicht analysiert werden konnte.
 */
public record ExtractionFailure(String document, String message) {
}
