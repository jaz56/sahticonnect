package tn.sahticonnect.orchestrateur.client;

/**
 * La requête n'a pas pu partir (connexion refusée, hôte inconnu, délai de connexion dépassé) : le service n'a
 * rien reçu. On peut donc la rejouer sans risque, même pour une création (ambulance, feuille de soins…).
 */
public class ConnexionImpossibleException extends ServiceIndisponibleException {

    public ConnexionImpossibleException(String service, String detail, Throwable cause) {
        super(service, detail, cause);
    }
}
