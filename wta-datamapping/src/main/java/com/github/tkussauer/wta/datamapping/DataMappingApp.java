package com.github.tkussauer.wta.datamapping;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Anwendung 3: Extrahiert alle zu befuellenden Variablen aus einem Verzeichnis von Word-Vorlagen
 * als Grundlage fuer das Datenmapping.
 *
 * <pre>
 * java -jar wta-datamapping.jar [-n] [-p regex]... [--ohne-textmarken] &lt;Verzeichnis&gt; &lt;Ausgabedatei.xlsx&gt;
 * </pre>
 */
public final class DataMappingApp {

    static final int EXIT_OK = 0;
    static final int EXIT_ERROR = 1;
    static final int EXIT_USAGE = 2;

    private static final String USAGE = """
            Verwendung: java -jar wta-datamapping.jar [Optionen] <Verzeichnis> <Ausgabedatei.xlsx>

            Extrahiert alle zu befüllenden Variablen der Word-Vorlagen (docx, dotx, docm, dotm,
            doc, dot): Seriendruckfelder, Dokumentvariablen und -eigenschaften, Eingabe-
            aufforderungen, Formularfelder, Inhaltssteuerelemente, Textmarken und Platzhalter
            wie «Name», {{Name}}, ${Name}, <<Name>> oder [[Name]].

            Optionen:
              -p, --platzhalter <Regex>   zusätzliches Platzhaltermuster (Gruppe 1 = Name),
                                          mehrfach möglich, z. B. -p "%([A-Za-z_]+)%"
                  --ohne-textmarken       Textmarken nicht als Variablen aufnehmen
              -n, --nicht-rekursiv        Unterverzeichnisse nicht durchsuchen
              -h, --hilfe                 Diese Hilfe anzeigen
            """;

    private DataMappingApp() {
    }

    public static void main(String[] args) {
        System.exit(run(args));
    }

    static int run(String[] args) {
        System.setProperty("java.awt.headless", "true");
        boolean recursive = true;
        boolean bookmarks = true;
        List<Pattern> placeholders = new ArrayList<>(DataMappingAnalysis.DEFAULT_PLACEHOLDERS);
        List<String> positional = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "-h", "--hilfe", "--help" -> {
                    System.out.print(USAGE);
                    return EXIT_OK;
                }
                case "-n", "--nicht-rekursiv" -> recursive = false;
                case "--ohne-textmarken" -> bookmarks = false;
                case "-p", "--platzhalter" -> {
                    if (i + 1 >= args.length) {
                        System.err.println("Option " + arg + " erwartet ein Muster.");
                        return EXIT_USAGE;
                    }
                    try {
                        placeholders.add(Pattern.compile(args[++i]));
                    } catch (PatternSyntaxException e) {
                        System.err.println("Ungültiges Platzhaltermuster: " + e.getMessage());
                        return EXIT_USAGE;
                    }
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

        try {
            System.out.println("Extrahiere Variablen aus Word-Vorlagen in " + directory.toAbsolutePath());
            DataMappingAnalysis.Result result = new DataMappingAnalysis(placeholders, bookmarks)
                    .analyze(directory, recursive, System.out::println);
            new DataMappingReportWriter().write(result, output);

            Map<VariableKind, Long> counts = new EnumMap<>(VariableKind.class);
            result.templates().stream()
                    .flatMap(t -> t.occurrences().stream())
                    .map(VariableOccurrence::key)
                    .distinct()
                    .forEach(key -> counts.merge(VariableKind.valueOf(key.substring(0, key.indexOf(':'))), 1L, Long::sum));
            long total = counts.values().stream().mapToLong(Long::longValue).sum();
            System.out.printf("%n%d Vorlage(n) analysiert, %d Fehler, %d unterschiedliche Variable(n)%n",
                    result.templates().size(), result.failures().size(), total);
            counts.forEach((kind, count) -> System.out.printf("  %-22s %d%n", kind.label() + ":", count));
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
