package com.github.tkussauer.wta.fonts;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Anwendung 1: Extraktion aller Schriftstile aus einem Verzeichnis von Word-Vorlagen
 * zur Uebernahme in Quadient Inspire.
 *
 * <pre>
 * java -jar wta-font-extractor.jar [-n] &lt;Verzeichnis&gt; &lt;Ausgabedatei.xlsx&gt;
 * </pre>
 */
public final class FontExtractorApp {

    static final int EXIT_OK = 0;
    static final int EXIT_ERROR = 1;
    static final int EXIT_USAGE = 2;

    private static final String USAGE = """
            Verwendung: java -jar wta-font-extractor.jar [Optionen] <Verzeichnis> <Ausgabedatei.xlsx>

            Extrahiert alle Schriftstile der Word-Vorlagen (docx, dotx, docm, dotm, doc, dot)
            im Verzeichnis in das Excel-Arbeitsblatt "Schriftarten".

            Optionen:
              -n, --nicht-rekursiv   Unterverzeichnisse nicht durchsuchen
              -h, --hilfe            Diese Hilfe anzeigen
            """;

    private FontExtractorApp() {
    }

    public static void main(String[] args) {
        System.exit(run(args));
    }

    static int run(String[] args) {
        System.setProperty("java.awt.headless", "true");
        boolean recursive = true;
        List<String> positional = new ArrayList<>();
        for (String arg : args) {
            switch (arg) {
                case "-h", "--hilfe", "--help" -> {
                    System.out.print(USAGE);
                    return EXIT_OK;
                }
                case "-n", "--nicht-rekursiv" -> recursive = false;
                default -> {
                    if (arg.startsWith("-")) {
                        System.err.println("Unbekannte Option: " + arg);
                        System.err.print(USAGE);
                        return EXIT_USAGE;
                    }
                    positional.add(arg);
                }
            }
        }
        if (positional.size() != 2) {
            System.err.print(USAGE);
            return EXIT_USAGE;
        }
        Path directory = Path.of(positional.get(0));
        Path output = withXlsxExtension(Path.of(positional.get(1)));
        if (!Files.isDirectory(directory)) {
            System.err.println("Verzeichnis nicht gefunden: " + directory.toAbsolutePath());
            return EXIT_USAGE;
        }

        try {
            System.out.println("Analysiere Word-Vorlagen in " + directory.toAbsolutePath());
            FontAnalysis.Result result = new FontAnalysis().analyze(directory, recursive, System.out::println);
            new FontReportWriter().write(result, output);
            long styles = result.documents().stream()
                    .flatMap(d -> d.usages().stream())
                    .map(FontUsage::style)
                    .distinct()
                    .count();
            System.out.printf("%n%d Dokument(e) analysiert, %d Fehler, %d unterschiedliche Schriftstile.%n",
                    result.documents().size(), result.failures().size(), styles);
            System.out.println("Ergebnis: " + output.toAbsolutePath());
            return EXIT_OK;
        } catch (IOException e) {
            System.err.println("Fehler: " + e.getMessage());
            return EXIT_ERROR;
        }
    }

    static Path withXlsxExtension(Path output) {
        String name = output.getFileName().toString();
        if (name.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            return output;
        }
        return output.resolveSibling(name + ".xlsx");
    }
}
