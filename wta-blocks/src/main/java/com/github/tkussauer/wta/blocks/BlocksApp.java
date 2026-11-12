package com.github.tkussauer.wta.blocks;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Anwendung 5: Findet gleiche Bloecke in Word-Vorlagen (Kandidaten fuer externe Bloecke in
 * Quadient Inspire) und Vorlagen, die sich ab einer Schwelle aehneln.
 *
 * <pre>
 * java -jar wta-blocks.jar [-s 80] [-n] &lt;Verzeichnis&gt; &lt;Ausgabedatei.xlsx&gt;
 * </pre>
 */
public final class BlocksApp {

    static final int EXIT_OK = 0;
    static final int EXIT_ERROR = 1;
    static final int EXIT_USAGE = 2;

    private static final String USAGE = """
            Verwendung: java -jar wta-blocks.jar [Optionen] <Verzeichnis> <Ausgabedatei.xlsx>

            Findet Absatzfolgen, die in mehreren Word-Vorlagen gleich sind (Kandidaten für externe
            Blöcke in Quadient Inspire), und Vorlagen, die sich ab einer Schwelle ähneln.

            Optionen:
              -s, --schwelle <Prozent>      Ähnlichkeitsschwelle für Vorlagen (Standard: 80)
                  --min-absatz <Anzahl>     Mindestbuchstaben je Absatz für den Blockvergleich (Standard: 15)
                  --min-block <Anzahl>      Mindestbuchstaben je Block (Standard: 50)
                  --min-vorlagen <Anzahl>   Mindestanzahl Vorlagen je Block (Standard: 2)
              -n, --nicht-rekursiv          Unterverzeichnisse nicht durchsuchen
              -h, --hilfe                   Diese Hilfe anzeigen
            """;

    private BlocksApp() {
    }

    public static void main(String[] args) {
        System.exit(run(args));
    }

    static int run(String[] args) {
        System.setProperty("java.awt.headless", "true");
        boolean recursive = true;
        BlocksAnalysis.Settings defaults = BlocksAnalysis.Settings.DEFAULT;
        int threshold = (int) Math.round(defaults.threshold() * 100);
        int minParagraph = defaults.minParagraphLetters();
        int minBlock = defaults.minBlockLetters();
        int minDocuments = defaults.minDocuments();
        List<String> positional = new ArrayList<>();
        try {
            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                switch (arg) {
                    case "-h", "--hilfe", "--help" -> {
                        System.out.print(USAGE);
                        return EXIT_OK;
                    }
                    case "-n", "--nicht-rekursiv" -> recursive = false;
                    case "-s", "--schwelle" -> threshold = number(args, ++i, arg, 1, 100);
                    case "--min-absatz" -> minParagraph = number(args, ++i, arg, 0, 10_000);
                    case "--min-block" -> minBlock = number(args, ++i, arg, 0, 1_000_000);
                    case "--min-vorlagen" -> minDocuments = number(args, ++i, arg, 2, 100_000);
                    default -> {
                        if (arg.startsWith("-")) {
                            throw new IllegalArgumentException("Unbekannte Option: " + arg);
                        }
                        positional.add(arg);
                    }
                }
            }
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            System.err.print(USAGE);
            return EXIT_USAGE;
        }
        if (positional.size() != 2) {
            System.err.print(USAGE);
            return EXIT_USAGE;
        }
        Path input = Path.of(positional.get(0));
        Path output = withXlsxExtension(Path.of(positional.get(1)));
        if (!Files.exists(input)) {
            System.err.println("Verzeichnis nicht gefunden: " + input.toAbsolutePath());
            return EXIT_USAGE;
        }

        BlocksAnalysis.Settings settings = new BlocksAnalysis.Settings(threshold / 100.0, minParagraph, minBlock,
                minDocuments);
        try {
            System.out.println("Analysiere Word-Vorlagen in " + input.toAbsolutePath());
            BlocksAnalysis.Result result = new BlocksAnalysis(settings).analyze(input, recursive, System.out::println);
            new BlocksReportWriter().write(result, output);
            int pairs = result.similarity().pairs(settings.threshold()).size();
            int groups = result.similarity().groups(settings.threshold()).size();
            System.out.printf("%n%d Vorlage(n) analysiert, %d Fehler%n", result.templates().size(),
                    result.failures().size());
            System.out.printf("%d gleiche Blöcke, %d Vorlagenpaar(e) ab %d %% Ähnlichkeit in %d Gruppe(n)%n",
                    result.blocks().size(), pairs, threshold, groups);
            System.out.println("Ergebnis: " + output.toAbsolutePath());
            return EXIT_OK;
        } catch (IOException e) {
            System.err.println("Fehler: " + e.getMessage());
            return EXIT_ERROR;
        }
    }

    private static int number(String[] args, int index, String option, int min, int max) {
        if (index >= args.length) {
            throw new IllegalArgumentException("Option " + option + " erwartet eine Zahl.");
        }
        try {
            int value = Integer.parseInt(args[index].replace("%", "").strip());
            if (value < min || value > max) {
                throw new IllegalArgumentException(
                        "Option " + option + ": Wert muss zwischen " + min + " und " + max + " liegen.");
            }
            return value;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Option " + option + " erwartet eine Zahl: " + args[index]);
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
