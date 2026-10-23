package com.github.tkussauer.wta.fonts;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Extrahiert die Schriftstile eines Word-Dokuments.
 */
public interface FontExtractor {

    /**
     * @param file          zu analysierende Datei
     * @param documentName  Name, unter dem das Dokument im Ergebnis gefuehrt wird
     */
    DocumentFonts extract(Path file, String documentName) throws IOException;
}
