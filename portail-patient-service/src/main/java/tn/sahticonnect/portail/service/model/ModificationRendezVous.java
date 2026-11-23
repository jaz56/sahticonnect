package tn.sahticonnect.portail.service.model;

import tn.sahticonnect.portail.entity.StatutRendezVous;
import tn.sahticonnect.portail.entity.TypeConsultation;

/** Modification d'un rendez-vous (champs null = inchangés). statut : TERMINE ou ABSENT uniquement. */
public record ModificationRendezVous(String creneauId, TypeConsultation type, String motif, StatutRendezVous statut) {

    public boolean estVide() {
        return creneauId == null && type == null && motif == null && statut == null;
    }
}
