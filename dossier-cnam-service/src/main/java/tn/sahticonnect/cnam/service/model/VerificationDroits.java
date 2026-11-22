package tn.sahticonnect.cnam.service.model;

import tn.sahticonnect.cnam.entity.Assure;

/** Résultat de verifierDroitsCNAM. */
public record VerificationDroits(Assure assure, ResultatCouverture couverture, Montants montants) {
}
