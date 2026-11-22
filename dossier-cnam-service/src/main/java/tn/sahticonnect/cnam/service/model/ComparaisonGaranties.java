package tn.sahticonnect.cnam.service.model;

import java.math.BigDecimal;

/** Résultat de comparerGaranties. */
public record ComparaisonGaranties(
        ResultatGarantie resultatA,
        ResultatGarantie resultatB,
        BigDecimal differencePartPatient,
        String recommandation) {
}
