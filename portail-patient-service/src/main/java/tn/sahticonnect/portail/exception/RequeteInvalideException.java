package tn.sahticonnect.portail.exception;

import org.springframework.http.HttpStatus;

/** 400 : règle de cohérence de la requête non respectée (au-delà de la validation du contrat). */
public class RequeteInvalideException extends PortailException {

    public static final String CODE = "REQUETE_INVALIDE";

    public RequeteInvalideException(String message) {
        super(HttpStatus.BAD_REQUEST, CODE, message);
    }

    public RequeteInvalideException(String code, String message) {
        super(HttpStatus.BAD_REQUEST, code, message);
    }
}
