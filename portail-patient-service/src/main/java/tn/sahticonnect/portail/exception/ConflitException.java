package tn.sahticonnect.portail.exception;

import org.springframework.http.HttpStatus;

/** 409 : la requête est valide mais incompatible avec l'état actuel (créneau pris, mission en cours…). */
public class ConflitException extends PortailException {

    public ConflitException(String code, String message) {
        super(HttpStatus.CONFLICT, code, message);
    }

    public static ConflitException creneauDejaReserve(String creneauId) {
        return new ConflitException("CRENEAU_DEJA_RESERVE", "Le créneau " + creneauId + " n'est plus disponible");
    }
}
