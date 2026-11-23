package tn.sahticonnect.portail.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception métier : porte le statut HTTP et un code stable (PATIENT_INTROUVABLE,
 * CRENEAU_DEJA_RESERVE…), renvoyés au client dans le schéma Erreur du contrat.
 */
public abstract class PortailException extends RuntimeException {

    private final HttpStatus statut;
    private final String code;

    protected PortailException(HttpStatus statut, String code, String message) {
        super(message);
        this.statut = statut;
        this.code = code;
    }

    public HttpStatus getStatut() {
        return statut;
    }

    public String getCode() {
        return code;
    }
}
