package tn.sahticonnect.dashboard.service;

/** Patient, alerte ou intervention inconnu → erreur GraphQL extensions.code = NOT_FOUND. */
public class RessourceIntrouvableException extends RuntimeException {

    public RessourceIntrouvableException(String message) {
        super(message);
    }

    public static RessourceIntrouvableException patient(String id) {
        return new RessourceIntrouvableException("Patient inconnu : " + id);
    }

    public static RessourceIntrouvableException alerte(String id) {
        return new RessourceIntrouvableException("Alerte inconnue : " + id);
    }

    public static RessourceIntrouvableException intervention(String id) {
        return new RessourceIntrouvableException("Intervention inconnue : " + id);
    }
}
