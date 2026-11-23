package tn.sahticonnect.cnam.exception;

/**
 * Exception métier du dossier CNAM. Elle porte un code stable, destiné aux clients du service,
 * que {@code ErreurCnamExceptionResolver} place dans le &lt;detail&gt; de la SOAP Fault.
 */
public abstract class CnamException extends RuntimeException {

    private final String code;

    protected CnamException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
