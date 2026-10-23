package com.github.tkussauer.wta.complexity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Anwendung 2: Bewertet die Komplexitaet aller Word-Vorlagen eines Verzeichnisses
 * und ordnet sie den T-Shirt-Groessen S, M, L und XL zu.
 *
 * <pre>
 * java -jar wta-complexity.jar [-n] [-k schema.properties] &lt;Verzeichnis&gt; &lt;Ausgabedatei.xlsx&gt;
 * </pre>
 */
public final class ComplexityApp {

    static final int EXIT_OK = 0;
    static final int EXIT_ERROR = 1;
    static final int EXIT_USAGE = 2;

    private static final String USAGE = """
            Verwendung: java -jar wta-complexity.jar [Optionen] <Verzeichnis> <Ausgabedatei.xlsx>

            Bewertet die Komplexität der Word-Vorlagen (docx, dotx, docm, dotm, doc, dot)
            im Verzeichnis und ordnet sie den T-Shirt-Größen S, M, L und XL zu.

            Optionen:
              -k, --konfiguration <Datei>   eigenes Bewertungsschema (Properties-Datei)
              -n, --nicht-rekursiv          Unterverzeichnisse nicht durchsuchen
              -h, --hilfe                   Diese Hilfe anzeigen
            """;

    private ComplexityApp() {
    }

    public static void main(String[] args) {
        System.exit(run(args));
    }

    static int run(String[] args) {
        System.setProperty("java.awt.headless", "true");
        boolean recursive = true;
        Path configuration = null;
        List<String> positional = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "-h", "--hilfe", "--help" -> {
                    System.out.print(USAGE);
                    return EXIT_OK;
                }
                case "-n", "--nicht-rekursiv" -> recursive = false;
                case "-k", "--konfiguration" -> {
                    if (i + 1 >= args.length) {
                        System.err.println("Option " + arg + " erwartet einen Dateinamen.");
                        return EXIT_USAGE;
                    }
                    configuration = Path.of(args[++i]);
                }
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

        ComplexityModel model;
        try {
            model = configuration != null ? ComplexityModel.load(configuration) : ComplexityModel.defaults();
        } catch (IOException | IllegalArgumentException e) {
            System.err.println("Bewertungsschema ungültig: " + e.getMessage());
            return EXIT_USAGE;
        }

        try {
            System.out.println("Bewerte Word-Vorlagen in " + directory.toAbsolutePath());
            ComplexityAnalysis.Result result = new ComplexityAnalysis(model)
                    .analyze(directory, recursive, System.out::println);
            new ComplexityReportWriter().write(result, output);

            Map<TShirtSize, Integer> counts = new EnumMap<>(TShirtSize.class);
            result.assessments().forEach(a -> counts.merge(a.size(), 1, Integer::sum));
            System.out.printf("%n%d Vorlage(n) bewertet, %d Fehler: S=%d, M=%d, L=%d, XL=%d%n",
                    result.assessments().size(), result.failures().size(),
                    counts.getOrDefault(TShirtSize.S, 0), counts.getOrDefault(TShirtSize.M, 0),
                    counts.getOrDefault(TShirtSize.L, 0), counts.getOrDefault(TShirtSize.XL, 0));
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
