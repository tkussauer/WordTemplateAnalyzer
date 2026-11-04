package com.github.tkussauer.wta.datamapping;

/**
 * Eigenschaften eines Legacy-Formularfeldes (FORMTEXT, FORMCHECKBOX, FORMDROPDOWN).
 *
 * @param name      Name des Formularfeldes (zugleich Textmarke)
 * @param dataType  Datentyp, z. B. Text, Zahl, Datum, Ja/Nein
 * @param format    Formatangabe
 * @param options   Auswahlwerte (Dropdown)
 * @param value     Standardwert
 */
record FormFieldInfo(String name, String dataType, String format, String options, String value) {
}
