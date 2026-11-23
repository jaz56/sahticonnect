package tn.sahticonnect.portail.exception;

import org.springframework.http.HttpStatus;

/** 404 : ressource inexistante. */
public class RessourceIntrouvableException extends PortailException {

    public RessourceIntrouvableException(String code, String message) {
        super(HttpStatus.NOT_FOUND, code, message);
    }

    public static RessourceIntrouvableException patient(String id) {
        return new RessourceIntrouvableException("PATIENT_INTROUVABLE", "Aucun patient " + id);
    }

    public static RessourceIntrouvableException medecin(String id) {
        return new RessourceIntrouvableException("MEDECIN_INTROUVABLE", "Aucun médecin " + id);
    }

    public static RessourceIntrouvableException creneau(String id) {
        return new RessourceIntrouvableException("CRENEAU_INTROUVABLE", "Aucun créneau " + id);
    }

    public static RessourceIntrouvableException rendezVous(String id) {
        return new RessourceIntrouvableException("RDV_INTROUVABLE", "Aucun rendez-vous " + id);
    }

    public static RessourceIntrouvableException mission(String id) {
        return new RessourceIntrouvableException("MISSION_INTROUVABLE", "Aucune mission " + id);
    }
}
