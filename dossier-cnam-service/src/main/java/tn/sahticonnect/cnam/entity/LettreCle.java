package tn.sahticonnect.cnam.entity;

import java.math.BigDecimal;

/**
 * Lettres-clés (inspirées de la NGAP) et leur valeur fictive en DT.
 * Montant d'un acte = valeur × coefficient.
 */
public enum LettreCle {
    C("45"),   // consultation généraliste
    CS("60"),  // consultation spécialiste
    V("55"),   // visite à domicile
    K("3"),    // acte technique
    B("0.3"),  // biologie
    Z("2");    // radiologie

    private final BigDecimal valeur;

    LettreCle(String valeur) {
        this.valeur = new BigDecimal(valeur);
    }

    public BigDecimal getValeur() {
        return valeur;
    }
}
