package tn.sahticonnect.orchestrateur.client;

/**
 * Erreur TECHNIQUE d'un appel distant (service arrêté, délai dépassé, erreur 5xx…), par opposition à une
 * erreur métier. Seules les erreurs techniques sont réessayées (retry) et comptées par le circuit breaker.
 */
public class ServiceIndisponibleException extends RuntimeException {

    private final String service;

    public ServiceIndisponibleException(String service, String detail, Throwable cause) {
        super("Service " + service + " indisponible : " + detail, cause);
        this.service = service;
    }

    public String getService() {
        return service;
    }
}
