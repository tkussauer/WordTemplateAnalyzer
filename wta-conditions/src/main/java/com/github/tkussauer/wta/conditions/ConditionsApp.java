package com.github.tkussauer.wta.conditions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Anwendung 4: Extrahiert alle Bedingungen (IF, COMPARE, SKIPIF, NEXTIF) aus einer Word-Vorlage
 * oder allen Vorlagen eines Verzeichnisses.
 *
 * <pre>
 * java -jar wta-conditions.jar [-n] &lt;Datei oder Verzeichnis&gt; &lt;Ausgabedatei.xlsx&gt;
 * </pre>
 */
public final class ConditionsApp {

    static final int EXIT_OK = 0;
    static final int EXIT_ERROR = 1;
    static final int EXIT_USAGE = 2;

    private static final String USAGE = """
            Verwendung: java -jar wta-conditions.jar [Optionen] <Datei oder Verzeichnis> <Ausgabedatei.xlsx>

            Extrahiert alle Bedingungen (IF, COMPARE, SKIPIF, NEXTIF) inkl. Verschachtelung aus einer
            Word-Vorlage oder allen Vorlagen eines Verzeichnisses (docx, dotx, docm, dotm, doc, dot).

            Optionen:
              -n, --nicht-rekursiv   Unterverzeichnisse nicht durchsuchen
              -h, --hilfe            Diese Hilfe anzeigen
            """;

    private ConditionsApp() {
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
        Path input = Path.of(positional.get(0));
        Path output = withXlsxExtension(Path.of(positional.get(1)));
        if (!Files.exists(input)) {
            System.err.println("Datei oder Verzeichnis nicht gefunden: " + input.toAbsolutePath());
            return EXIT_USAGE;
        }

        try {
            System.out.println("Extrahiere Bedingungen aus " + input.toAbsolutePath());
            ConditionAnalysis.Result result = new ConditionAnalysis().analyze(input, recursive, System.out::println);
            new ConditionReportWriter().write(result, output);
            int conditions = result.templates().stream().mapToInt(t -> t.conditions().size()).sum();
            System.out.printf("%n%d Vorlage(n) analysiert, %d Fehler, %d Bedingung(en)%n",
                    result.templates().size(), result.failures().size(), conditions);
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
