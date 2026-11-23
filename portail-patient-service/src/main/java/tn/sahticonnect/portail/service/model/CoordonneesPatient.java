package tn.sahticonnect.portail.service.model;

import tn.sahticonnect.portail.entity.ContactUrgence;

/** Coordonnées modifiables d'un patient (champs null = inchangés). */
public record CoordonneesPatient(String telephone, String email, String adresse, ContactUrgence contactUrgence) {

    public boolean estVide() {
        return telephone == null && email == null && adresse == null && contactUrgence == null;
    }
}
