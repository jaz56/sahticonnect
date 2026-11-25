package tn.sahticonnect.portail.exception;

import org.springframework.http.HttpStatus;

/** 403 : un patient connecté (en-tête X-Patient-Id) accède aux données d'un autre patient. */
public class AccesRefuseException extends PortailException {

    public AccesRefuseException(String patientConnecte, String patientDemande) {
        super(HttpStatus.FORBIDDEN, "ACCES_REFUSE",
                "Le patient " + patientConnecte + " n'a pas accès aux données de " + patientDemande);
    }
}
