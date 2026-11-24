package tn.sahticonnect.orchestrateur.client;

/**
 * Refus métier d'un service : 4xx REST (code du schéma Erreur), SOAP Fault « Client » (code du &lt;detail&gt;),
 * erreur GraphQL NOT_FOUND / BAD_REQUEST. Jamais réessayée : la même requête donnerait le même refus.
 */
public class ErreurMetierException extends RuntimeException {

    private final String service;
    private final String code;

    public ErreurMetierException(String service, String code, String message) {
        super(message);
        this.service = service;
        this.code = code;
    }

    public String getService() {
        return service;
    }

    public String getCode() {
        return code;
    }
}
