package tn.sahticonnect.orchestrateur.client;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;

import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.http.HttpConnectTimeoutException;

/** Classement des erreurs techniques des clients, et vue « service indisponible » pour le workflow. */
public final class Pannes {

    private Pannes() {
    }

    /** Erreur technique → ConnexionImpossibleException (rien n'est parti) ou ReponseAbsenteException. */
    public static ServiceIndisponibleException technique(String service, Throwable erreur) {
        for (Throwable cause = erreur; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConnectException || cause instanceof UnknownHostException
                    || cause instanceof NoRouteToHostException || cause instanceof HttpConnectTimeoutException
                    || (cause instanceof SocketTimeoutException && cause.getMessage() != null
                        && cause.getMessage().toLowerCase().contains("connect"))) {
                return new ConnexionImpossibleException(service, message(cause), erreur);
            }
        }
        return new ReponseAbsenteException(service, message(racine(erreur)), erreur);
    }

    /**
     * Le service est-il indisponible ? Erreur technique après les retries, ou circuit breaker ouvert
     * (CallNotPermittedException : on n'essaie même plus, le service est en panne depuis un moment).
     */
    public static boolean estIndisponible(Throwable erreur) {
        return erreur instanceof ServiceIndisponibleException || erreur instanceof CallNotPermittedException;
    }

    /** Message court pour la trace d'une étape. */
    public static String decrire(Throwable erreur) {
        if (erreur instanceof CallNotPermittedException e) {
            return "circuit ouvert (" + e.getCausingCircuitBreakerName() + " en panne depuis plusieurs appels), appel non tenté";
        }
        if (erreur instanceof ErreurMetierException e) {
            return e.getCode() + " : " + e.getMessage();
        }
        return erreur.getMessage();
    }

    private static Throwable racine(Throwable erreur) {
        Throwable cause = erreur;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause;
    }

    private static String message(Throwable cause) {
        return cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
    }
}
