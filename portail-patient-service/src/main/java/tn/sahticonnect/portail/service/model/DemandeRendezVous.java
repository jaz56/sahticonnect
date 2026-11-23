package tn.sahticonnect.portail.service.model;

import tn.sahticonnect.portail.entity.TypeConsultation;

/** Prise de rendez-vous : medecinId et creneauId sont facultatifs pour une URGENCE. */
public record DemandeRendezVous(String patientId, String medecinId, String creneauId, TypeConsultation type,
                                String motif, String referenceAlerte) {
}
