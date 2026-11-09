package com.github.tkussauer.wta.datamapping;

import com.github.tkussauer.wta.common.DocxLayout;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Leitet aus den Word-Variablen ein konsolidiertes, dublettenfreies Datenmodell fuer Quadient Inspire ab.
 * <ul>
 *     <li>Namen werden zu gueltigen Inspire-Bezeichnern (PascalCase, ohne Umlaute und Sonderzeichen).</li>
 *     <li>Variablen mit gleichem Bezeichner werden zusammengefuehrt, z. B. {@code MERGEFIELD Strasse},
 *         {@code «Straße»} und {@code strasse}.</li>
 *     <li>Hierarchien aus Namen ({@code vertrag.nummer}) oder XML-Datenbindungen ({@code /vertrag/nummer})
 *         werden uebernommen, sonst wird eine fachliche Gruppe anhand des Namens vorgeschlagen.</li>
 * </ul>
 */
public final class DataModelBuilder {

    /** Gruppe fuer Dokumenteigenschaften und -variablen (Steuerdaten statt Inhaltsdaten). */
    static final String DOCUMENT_GROUP = "Dokument";
    /** Gruppe fuer Felder ohne erkennbare fachliche Zuordnung. */
    static final String DEFAULT_GROUP = "Allgemein";

    /**
     * Fachliche Gruppen mit Schluesselwoertern, in Pruefreihenfolge (spezifische Gruppen zuerst).
     * Schluesselwoerter bis 3 Zeichen muessen am Anfang oder Ende des Namens stehen.
     */
    private static final Map<String, List<String>> GROUPS = new LinkedHashMap<>();

    static {
        GROUPS.put("Absender", List.of("sachbearbeiter", "bearbeiter", "absender", "unterschrift", "abteilung",
                "durchwahl", "unserzeichen"));
        GROUPS.put("Rechnung", List.of("rechnung", "betrag", "summe", "preis", "netto", "brutto", "mwst", "ust",
                "steuer", "rabatt", "gebuehr", "iban", "bic", "bank", "konto", "zahlung", "faellig", "mahn"));
        GROUPS.put("Vertrag", List.of("vertrag", "police", "versicherung", "tarif", "laufzeit", "beginn",
                "ablauf", "kuendigung", "produkt"));
        GROUPS.put("Adresse", List.of("strasse", "hausnummer", "hausnr", "plz", "postleitzahl", "ort", "stadt",
                "land", "postfach", "adresse", "adresszusatz"));
        GROUPS.put("Kontakt", List.of("email", "mail", "telefon", "tel", "fax", "mobil", "handy"));
        GROUPS.put("Empfaenger", List.of("anrede", "titel", "vorname", "nachname", "name", "kunde", "geburt",
                "firma"));
    }

    /** Reihenfolge der fachlichen Gruppen im Ergebnis, wie in einem Anschreiben. */
    private static final List<String> GROUP_ORDER = List.of(
            "Empfaenger", "Adresse", "Kontakt", "Vertrag", "Rechnung", "Absender");

    private static final Pattern MONEY = Pattern.compile(
            "betrag|summe|preis|netto|brutto|gebuehr|kosten|praemie|saldo|rate|amount|price|total");
    private static final Pattern INTEGER = Pattern.compile("anzahl|menge|stueck|count|nummer");
    private static final Pattern XPATH_PREDICATE = Pattern.compile("\\[[^]]*]");
    private static final Pattern NAMESPACE_PREFIX = Pattern.compile("^[A-Za-z_][\\w.-]*:");

    private DataModelBuilder() {
    }

    /** Ein Feld im Aufbau, mit allen zugeordneten Word-Vorkommen. */
    private static final class Draft {
        final String group;
        final Map<String, Integer> nameVotes = new HashMap<>();
        final List<VariableOccurrence> occurrences = new ArrayList<>();
        final Set<String> originalKeys = new LinkedHashSet<>();

        Draft(String group) {
            this.group = group;
        }
    }

    /**
     * @return Felder sortiert nach Gruppe (fachliche Gruppen, dann weitere, zuletzt Allgemein und Dokument)
     *         und Feldname
     */
    public static List<ModelField> build(List<TemplateVariables> templates) {
        Map<String, Draft> drafts = new LinkedHashMap<>();
        for (TemplateVariables template : templates) {
            for (VariableOccurrence occurrence : template.occurrences()) {
                if (!isModelRelevant(occurrence)) {
                    continue;
                }
                String[] location = locate(occurrence);
                String group = location[0];
                String name = location[1];
                Draft draft = drafts.computeIfAbsent(
                        (group + "." + name).toLowerCase(Locale.ROOT), k -> new Draft(group));
                draft.nameVotes.merge(name, 1, Integer::sum);
                draft.occurrences.add(occurrence);
                draft.originalKeys.add(occurrence.key());
            }
        }
        List<ModelField> fields = new ArrayList<>();
        for (Draft draft : drafts.values()) {
            fields.add(toField(draft));
        }
        addCrossFieldNotes(fields);
        fields.sort(Comparator.comparingInt((ModelField f) -> groupRank(f.group()))
                .thenComparing(ModelField::group, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(ModelField::name, String.CASE_INSENSITIVE_ORDER));
        return fields;
    }

    /** Variablen ohne verwertbaren Namen oder reine Container gehoeren nicht ins Datenmodell. */
    private static boolean isModelRelevant(VariableOccurrence occurrence) {
        String name = occurrence.name();
        return !name.startsWith("(") && !"Gruppe".equals(occurrence.dataType())
                && !identifier(name).isEmpty();
    }

    /** @return {Gruppe, Feldname} */
    static String[] locate(VariableOccurrence occurrence) {
        String source = occurrence.binding() != null && !occurrence.binding().isBlank()
                ? occurrence.binding() : occurrence.name();
        List<String> segments = segments(source, occurrence.binding() != null);
        if (segments.size() > 1) {
            List<String> groups = segments.subList(0, segments.size() - 1).stream()
                    .map(DataModelBuilder::identifier)
                    .filter(s -> !s.isEmpty())
                    .toList();
            if (!groups.isEmpty()) {
                return new String[] {String.join(".", groups), identifier(segments.get(segments.size() - 1))};
            }
        }
        String name = identifier(occurrence.name());
        if (occurrence.kind() == VariableKind.DOC_PROPERTY || occurrence.kind() == VariableKind.DOC_VARIABLE) {
            return new String[] {DOCUMENT_GROUP, name};
        }
        return new String[] {suggestGroup(name), name};
    }

    private static List<String> segments(String source, boolean xpath) {
        String cleaned = source.strip();
        if (xpath) {
            cleaned = XPATH_PREDICATE.matcher(cleaned).replaceAll("");
        }
        List<String> result = new ArrayList<>();
        for (String segment : cleaned.split("[./\\\\]+")) {
            String part = NAMESPACE_PREFIX.matcher(segment.strip()).replaceFirst("");
            if (!part.isEmpty() && !part.startsWith("@")) {
                result.add(part);
            }
        }
        return result;
    }

    static String suggestGroup(String identifier) {
        String lower = identifier.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, List<String>> group : GROUPS.entrySet()) {
            for (String keyword : group.getValue()) {
                boolean match = keyword.length() <= 3
                        ? lower.equals(keyword) || lower.startsWith(keyword) || lower.endsWith(keyword)
                        : lower.contains(keyword);
                if (match) {
                    return group.getKey();
                }
            }
        }
        return DEFAULT_GROUP;
    }

    /**
     * Wandelt einen Word-Namen in einen Inspire-Bezeichner: Umlaute ausgeschrieben, Woerter in
     * PascalCase, nur Buchstaben und Ziffern, beginnend mit einem Buchstaben.
     */
    static String identifier(String raw) {
        String text = raw.replace("ä", "ae").replace("ö", "oe").replace("ü", "ue")
                .replace("Ä", "Ae").replace("Ö", "Oe").replace("Ü", "Ue").replace("ß", "ss");
        text = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        StringBuilder result = new StringBuilder();
        for (String word : text.split("[^A-Za-z0-9]+")) {
            if (!word.isEmpty()) {
                result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            }
        }
        if (!result.isEmpty() && Character.isDigit(result.charAt(0))) {
            result.insert(0, 'F');
        }
        return result.toString();
    }

    private static ModelField toField(Draft draft) {
        // Haeufigste Schreibweise gewinnt, bei Gleichstand die alphabetisch erste
        String name = draft.nameVotes.entrySet().stream()
                .max(Map.Entry.<String, Integer>comparingByValue()
                        .thenComparing(Map.Entry.comparingByKey(Comparator.reverseOrder())))
                .map(Map.Entry::getKey)
                .orElseThrow();
        Set<String> formats = new LinkedHashSet<>();
        Set<String> values = new LinkedHashSet<>();
        Set<String> templates = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        Set<String> sources = new LinkedHashSet<>();
        Set<String> certainTypes = new LinkedHashSet<>();
        Set<String> guessedTypes = new LinkedHashSet<>();
        String example = null;
        boolean conditional = true;
        boolean onlyDefinitions = true;
        boolean onlyBookmarks = true;
        boolean computed = false;
        boolean onlyHidden = true;
        boolean image = false;
        for (VariableOccurrence o : draft.occurrences) {
            templates.add(o.document());
            sources.add(source(o));
            onlyHidden &= o.area().endsWith(DocxLayout.NOT_DISPLAYED);
            if (o.format() != null && !o.format().isBlank()) {
                formats.add(o.format());
            }
            if (o.options() != null && !o.options().isBlank()) {
                for (String option : o.options().split("\\s*\\|\\s*")) {
                    values.add(option);
                }
            }
            if (example == null && o.value() != null && !o.value().isBlank()) {
                example = o.value();
            }
            if (!o.isDefinition()) {
                onlyDefinitions = false;
                conditional &= o.inCondition();
            }
            onlyBookmarks &= o.kind() == VariableKind.BOOKMARK && "Textmarke".equals(o.fieldType());
            String dataType = o.dataType();
            if (dataType == null) {
                continue;
            }
            if (dataType.startsWith("berechnet")) {
                computed = true;
            } else if (dataType.endsWith(DataTypes.GUESSED)) {
                guessedTypes.add(dataType.substring(0, dataType.length() - DataTypes.GUESSED.length()));
            } else {
                image |= DataTypes.IMAGE.equals(dataType);
                certainTypes.add(dataType);
            }
        }

        List<String> notes = new ArrayList<>();
        InspireType type = resolveType(name, certainTypes, guessedTypes, formats, notes);
        if (computed) {
            notes.add("Berechneter Wert (SET) – in Inspire als Formel/Skript abbilden");
        }
        if (image) {
            notes.add("Bildreferenz (Pfad/ID) – in Inspire als Bildvariable einbinden");
        }
        if (onlyBookmarks) {
            notes.add("Nur Textmarke – prüfen, ob sie befüllt oder nur als Sprungmarke genutzt wird");
        }
        if (onlyDefinitions) {
            notes.add("Nur in der Vorlage definiert, im Text nicht verwendet");
            conditional = false;
        }
        if (onlyHidden) {
            notes.add("Nur in nicht angezeigten Kopf-/Fußzeilen – prüfen, ob das Feld benötigt wird");
        }
        if (formats.size() > 1) {
            notes.add("Unterschiedliche Formate in den Vorlagen");
        }
        return new ModelField(draft.group, name, type, formats, values, example, conditional, templates, sources,
                draft.originalKeys.size(), notes);
    }

    private static InspireType resolveType(String name, Set<String> certain, Set<String> guessed,
                                           Set<String> formats, List<String> notes) {
        Set<String> specific = new LinkedHashSet<>(certain);
        specific.remove(DataTypes.TEXT);
        String dataType;
        if (specific.size() == 1) {
            dataType = specific.iterator().next();
            if (certain.contains(DataTypes.TEXT)) {
                notes.add("Uneinheitlicher Datentyp: " + String.join(", ", certain) + " – " + dataType
                        + " vorgeschlagen");
            }
        } else if (specific.size() > 1) {
            notes.add("Widersprüchliche Datentypen: " + String.join(", ", specific) + " – String vorgeschlagen");
            return InspireType.STRING;
        } else if (!guessed.isEmpty()) {
            dataType = guessed.iterator().next();
            notes.add("Datentyp aus dem Namen abgeleitet");
        } else {
            dataType = DataTypes.TEXT;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        return switch (dataType) {
            case DataTypes.DATE -> InspireType.DATE_TIME;
            case DataTypes.BOOLEAN -> InspireType.BOOL;
            case "Liste (wiederholend)" -> InspireType.ARRAY;
            case DataTypes.NUMBER -> {
                if (MONEY.matcher(lower).find()) {
                    yield InspireType.CURRENCY;
                }
                boolean decimals = formats.stream().anyMatch(f -> f.matches(".*[.,][0#].*"));
                yield decimals || !INTEGER.matcher(lower).find() ? InspireType.DOUBLE : InspireType.INT;
            }
            default -> InspireType.STRING;
        };
    }

    /** Hinweise auf moegliche Dubletten, die nicht automatisch zusammengefuehrt werden. */
    private static void addCrossFieldNotes(List<ModelField> fields) {
        for (ModelField field : fields) {
            for (ModelField other : fields) {
                if (field == other) {
                    continue;
                }
                String a = field.name().toLowerCase(Locale.ROOT);
                String b = other.name().toLowerCase(Locale.ROOT);
                if (a.equals(b)) {
                    field.notes().add("Gleicher Feldname auch in Gruppe " + other.group() + " – Dublette prüfen");
                } else if (a.length() >= 5 && b.length() >= 5 && levenshtein(a, b) == 1) {
                    field.notes().add("Ähnlicher Feldname: " + other.group() + "." + other.name());
                }
            }
        }
    }

    private static String source(VariableOccurrence o) {
        if (o.isDefinition()) {
            return o.kind().label() + " (definiert) " + o.name();
        }
        return switch (o.kind()) {
            case PLACEHOLDER -> o.fieldType();
            case CONTENT_CONTROL -> "Inhaltssteuerelement " + o.name();
            case BOOKMARK -> ("Textmarke".equals(o.fieldType()) ? "Textmarke" : o.fieldType()) + " " + o.name();
            default -> o.fieldType() + " " + o.name();
        };
    }

    private static int groupRank(String group) {
        int index = GROUP_ORDER.indexOf(group);
        if (index >= 0) {
            return index;
        }
        if (DEFAULT_GROUP.equals(group)) {
            return 1000;
        }
        if (DOCUMENT_GROUP.equals(group)) {
            return 1001;
        }
        // Gruppen aus Namen/Datenbindungen zwischen den fachlichen Gruppen und "Allgemein"
        return 100;
    }

    static int levenshtein(String a, String b) {
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[b.length()];
    }
}
