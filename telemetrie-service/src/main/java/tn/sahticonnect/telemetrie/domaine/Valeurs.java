package tn.sahticonnect.telemetrie.domaine;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** Formatage des valeurs à la française : 142 → « 142 », 39.5 → « 39,5 ». */
public final class Valeurs {

    private Valeurs() {
    }

    public static String formater(double valeur) {
        DecimalFormat format = new DecimalFormat("0.#", DecimalFormatSymbols.getInstance(Locale.FRANCE));
        return format.format(valeur);
    }
}
