package tn.sahticonnect.cnam.exception;

/** Acte coté invalide (coefficient absent ou ≤ 0) → fault Client, code ACTE_INVALIDE. */
public class ActeInvalideException extends CnamException {

    public static final String CODE = "ACTE_INVALIDE";

    public ActeInvalideException(String message) {
        super(CODE, message);
    }
}
