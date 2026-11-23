package tn.sahticonnect.dashboard.service;

/** Données invalides (format d'identifiant, montant négatif, pagination…) → extensions.code = BAD_REQUEST. */
public class DonneesInvalidesException extends RuntimeException {

    public DonneesInvalidesException(String message) {
        super(message);
    }
}
