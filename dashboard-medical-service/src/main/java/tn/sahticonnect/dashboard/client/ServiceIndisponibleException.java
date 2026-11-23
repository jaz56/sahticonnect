package tn.sahticonnect.dashboard.client;

/**
 * Un service distant n'a pas répondu (panne, délai de 3 s dépassé, erreur technique).
 * Le champ GraphQL concerné vaut null et une erreur SERVICE_INDISPONIBLE est ajoutée à la réponse.
 */
public class ServiceIndisponibleException extends RuntimeException {

    public static final String TELEMETRIE = "telemetrie";
    public static final String DOSSIER_CNAM = "dossier-cnam";

    private final String service;

    public ServiceIndisponibleException(String service, String detail, Throwable cause) {
        super("Service " + service + " indisponible : " + detail, cause);
        this.service = service;
    }

    public String getService() {
        return service;
    }
}
