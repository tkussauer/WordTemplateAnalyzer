package com.github.tkussauer.wta.datamapping;

import java.util.Locale;

/**
 * Ein Vorkommen einer Variablen in einer Vorlage.
 */
public final class VariableOccurrence {

    /** Feldart fuer Variablen, die nur definiert, aber nicht im Text verwendet werden. */
    public static final String DEFINITION = "Definition";

    private final String document;
    private final String area;
    private final VariableKind kind;
    private final String fieldType;
    private final String name;
    private String dataType;
    private String format;
    private String options;
    private String value;
    private String binding;
    private String fieldCode;
    private boolean inCondition;
    private String condition;
    private String context;

    public VariableOccurrence(String document, String area, VariableKind kind, String fieldType, String name) {
        this.document = document;
        this.area = area;
        this.kind = kind;
        this.fieldType = fieldType;
        this.name = name;
    }

    /** Schluessel fuer die Zusammenfassung: Word behandelt Feldnamen ohne Gross-/Kleinschreibung. */
    public String key() {
        return kind.name() + ":" + name.toLowerCase(Locale.ROOT);
    }

    public boolean isDefinition() {
        return DEFINITION.equals(fieldType);
    }

    public String document() {
        return document;
    }

    public String area() {
        return area;
    }

    public VariableKind kind() {
        return kind;
    }

    /** Konkrete Art, z. B. MERGEFIELD, SET, Datum (Inhaltssteuerelement) oder «…». */
    public String fieldType() {
        return fieldType;
    }

    public String name() {
        return name;
    }

    public String dataType() {
        return dataType;
    }

    public String format() {
        return format;
    }

    /** Auswahlwerte von Listen. */
    public String options() {
        return options;
    }

    /** Standardwert oder hinterlegter Wert. */
    public String value() {
        return value;
    }

    /** XPath einer Datenbindung (Inhaltssteuerelemente). */
    public String binding() {
        return binding;
    }

    public String fieldCode() {
        return fieldCode;
    }

    /** {@code true}, wenn die Variable innerhalb eines IF-Feldes (Bedingung oder bedingter Text) steht. */
    public boolean inCondition() {
        return inCondition;
    }

    public String condition() {
        return condition;
    }

    public String context() {
        return context;
    }

    VariableOccurrence dataType(String dataType) {
        this.dataType = dataType;
        return this;
    }

    VariableOccurrence format(String format) {
        this.format = format;
        return this;
    }

    VariableOccurrence options(String options) {
        this.options = options;
        return this;
    }

    VariableOccurrence value(String value) {
        this.value = value;
        return this;
    }

    VariableOccurrence binding(String binding) {
        this.binding = binding;
        return this;
    }

    VariableOccurrence fieldCode(String fieldCode) {
        this.fieldCode = fieldCode;
        return this;
    }

    VariableOccurrence inCondition(boolean inCondition) {
        this.inCondition = inCondition;
        return this;
    }

    VariableOccurrence condition(String condition) {
        this.condition = condition;
        return this;
    }

    VariableOccurrence context(String context) {
        this.context = context;
        return this;
    }
}
