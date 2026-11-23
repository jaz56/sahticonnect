package tn.sahticonnect.portail.service.model;

import tn.sahticonnect.portail.entity.StatutRendezVous;

import java.time.LocalDate;

/** Filtres facultatifs de GET /rendez-vous. */
public record FiltreRendezVous(String patientId, String medecinId, LocalDate date, StatutRendezVous statut) {
}
