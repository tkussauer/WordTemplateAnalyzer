package com.github.tkussauer.wta.common;

/**
 * Empfaenger fuer den Inhalt eines Word-Dokuments als Ereignisstrom, wie ihn {@link WordDocumentReader}
 * fuer docx- und doc-Dateien gleichermassen liefert.
 * <p>
 * Ein Feld besteht aus {@link #beginField()}, Feldcode ({@link #fieldCode}), optional
 * {@link #separateField()} gefolgt vom Ergebnistext ({@link #text}) und {@link #endField()}.
 * Felder koennen verschachtelt sein.
 */
public interface WordEventListener {

    /** Beginn eines Dokumentbereichs, z. B. "Hauptdokument" oder "Kopfzeile (nicht angezeigt)". */
    void area(String area);

    void paragraphStart();

    void paragraphEnd();

    /** Sichtbarer Text oder Feldergebnis. */
    void text(String text);

    void beginField();

    void fieldCode(String code);

    void separateField();

    void endField();
}
