package com.github.tkussauer.wta.datamapping;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Zerlegter Feldcode, z. B. {@code MERGEFIELD Datum \@ "dd.MM.yyyy" \* MERGEFORMAT}.
 *
 * @param keyword   Feldname in Grossbuchstaben (MERGEFIELD, IF, ...)
 * @param arguments Argumente ohne Anfuehrungszeichen; verschachtelte Felder als {@code {...}}
 * @param switches  Schalter mit Wert, z. B. {@code \@ -> dd.MM.yyyy}
 */
record FieldCode(String keyword, List<String> arguments, Map<String, String> switches) {

    /** Schalter, denen ein Wert folgt. */
    private static final Set<String> VALUE_SWITCHES = Set.of("\\@", "\\#", "\\*", "\\b", "\\f", "\\d", "\\o");

    static FieldCode parse(String code) {
        List<String> tokens = tokenize(code);
        if (tokens.isEmpty()) {
            return new FieldCode("", List.of(), Map.of());
        }
        String first = tokens.get(0);
        // Schluesselwort sind die fuehrenden Buchstaben, z. B. auch bei "IF="
        int end = 0;
        while (end < first.length() && Character.isLetter(first.charAt(end))) {
            end++;
        }
        String keyword = first.substring(0, end).toUpperCase(Locale.ROOT);
        List<String> arguments = new ArrayList<>();
        if (end < first.length()) {
            arguments.add(first.substring(end));
        }
        Map<String, String> switches = new LinkedHashMap<>();
        for (int i = 1; i < tokens.size(); i++) {
            String token = tokens.get(i);
            if (token.startsWith("\\") && token.length() == 2) {
                String value = "";
                if (VALUE_SWITCHES.contains(token) && i + 1 < tokens.size() && !isSwitch(tokens.get(i + 1))) {
                    value = unquote(tokens.get(++i));
                }
                switches.merge(token, value, (a, b) -> a + ", " + b);
            } else {
                arguments.add(unquote(token));
            }
        }
        return new FieldCode(keyword, arguments, switches);
    }

    String argument(int index) {
        return index < arguments.size() ? arguments.get(index).strip() : "";
    }

    private static boolean isSwitch(String token) {
        return token.startsWith("\\") && token.length() == 2;
    }

    private static String unquote(String token) {
        if (token.length() >= 2 && token.startsWith("\"") && token.endsWith("\"")) {
            return token.substring(1, token.length() - 1).replace("\\\"", "\"");
        }
        return token;
    }

    /** Zerlegt an Leerraum; Anfuehrungszeichen und verschachtelte Felder {...} bleiben zusammen. */
    static List<String> tokenize(String code) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        int braces = 0;
        for (int i = 0; i < code.length(); i++) {
            char c = code.charAt(i);
            if (c == '\\' && quoted && i + 1 < code.length() && code.charAt(i + 1) == '"') {
                current.append(c).append(code.charAt(++i));
            } else if (c == '"' && braces == 0) {
                quoted = !quoted;
                current.append(c);
            } else if (c == '{' && !quoted) {
                braces++;
                current.append(c);
            } else if (c == '}' && !quoted && braces > 0) {
                braces--;
                current.append(c);
            } else if (Character.isWhitespace(c) && !quoted && braces == 0) {
                if (!current.isEmpty()) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }
        if (!current.isEmpty()) {
            tokens.add(current.toString());
        }
        return tokens;
    }
}
