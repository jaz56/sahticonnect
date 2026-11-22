package tn.sahticonnect.cnam.service.model;

import java.math.BigDecimal;

/** Répartition d'un montant : total = part CNAM + part patient (DT, 3 décimales). */
public record Montants(BigDecimal total, BigDecimal partCNAM, BigDecimal partPatient) {
}
