package tn.sahticonnect.portail.service.model;

/** Demande d'ambulance ; adresse null = adresse du profil, position facultative. */
public record DemandeAmbulance(String patientId, String referenceAlerte, String motif, String adresse,
                               Double latitude, Double longitude) {
}
