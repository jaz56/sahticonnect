package tn.sahticonnect.orchestrateur.client;

/**
 * La requête est partie mais aucune réponse exploitable n'est revenue (délai de lecture dépassé, erreur 5xx…) :
 * le service l'a PEUT-ÊTRE traitée. Rejouée seulement pour les opérations idempotentes.
 */
public class ReponseAbsenteException extends ServiceIndisponibleException {

    public ReponseAbsenteException(String service, String detail, Throwable cause) {
        super(service, detail, cause);
    }
}
