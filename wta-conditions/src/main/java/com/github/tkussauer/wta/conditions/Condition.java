package com.github.tkussauer.wta.conditions;

import java.util.List;

/**
 * Eine Bedingung aus einer Vorlage.
 *
 * @param document      Vorlage
 * @param area          Bereich (Hauptdokument, Kopfzeile, ...; ggf. "(nicht angezeigt)")
 * @param id            hierarchische Nummer, z. B. {@code 2.1} fuer die erste Bedingung in Bedingung 2
 * @param level         Verschachtelungstiefe (1 = oberste Ebene)
 * @param position      Lage in der uebergeordneten Bedingung, z. B. "Dann-Zweig von 2"
 * @param kind          IF, COMPARE, SKIPIF oder NEXTIF
 * @param left          linker Operand (lesbar, Felder als «Name»)
 * @param operator      Vergleichsoperator (=, &lt;&gt;, &lt;, &gt;, &lt;=, &gt;=)
 * @param right         rechter Operand (lesbar)
 * @param thenText      Text, wenn die Bedingung zutrifft (nur IF)
 * @param elseText      Text, wenn die Bedingung nicht zutrifft (nur IF)
 * @param readable      Bedingung in Worten
 * @param expression    normalisierter Ausdruck als Vorlage fuer die Umsetzung, z. B. {@code Anrede == "Herr"}
 * @param conditionFields Datenfelder in der Bedingung (Operanden)
 * @param textFields    Datenfelder im Dann-/Sonst-Text
 * @param compareValue  konstanter Vergleichswert oder {@code null}
 * @param notes         Hinweise
 * @param fieldCode     vollstaendiger Feldcode
 * @param result        zuletzt gespeichertes Ergebnis
 * @param context       Absatztext um das Feld
 */
public record Condition(String document,
                        String area,
                        String id,
                        int level,
                        String position,
                        String kind,
                        String left,
                        String operator,
                        String right,
                        String thenText,
                        String elseText,
                        String readable,
                        String expression,
                        List<String> conditionFields,
                        List<String> textFields,
                        String compareValue,
                        List<String> notes,
                        String fieldCode,
                        String result,
                        String context) {

    /** {@code true}, wenn die Bedingung in einer Kopf-/Fusszeile steht, die Word nicht anzeigt. */
    public boolean hidden() {
        return area.endsWith(" (nicht angezeigt)");
    }
}
