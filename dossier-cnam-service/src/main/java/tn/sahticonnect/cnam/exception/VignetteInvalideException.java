package tn.sahticonnect.cnam.exception;

/** La vignette fournie ne correspond pas au patient → fault Client, code VIGNETTE_INVALIDE. */
public class VignetteInvalideException extends CnamException {

    public static final String CODE = "VIGNETTE_INVALIDE";

    public VignetteInvalideException(String codeVignette, String identifiantPatient) {
        super(CODE, "La vignette " + codeVignette + " ne correspond pas au patient " + identifiantPatient);
    }
}
