package tn.sahticonnect.dashboard.correlation;

import org.slf4j.MDC;

import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Identifiant de corrélation : une valeur qui suit une même demande d'un service à l'autre (gateway → services,
 * orchestrateur → CNAM / portail / dashboard, dashboard → CNAM / telemetrie).
 * <ul>
 *   <li>HTTP : en-tête {@value #ENTETE} ; gRPC : métadonnée {@code x-correlation-id} ;</li>
 *   <li>rangé dans le MDC sous la clé {@value #CLE_MDC} : chaque ligne de journal commence par {@code [id]} ;</li>
 *   <li>workflow déclenché par une alerte gRPC (sans requête HTTP) : l'identifiant est celui du workflow (WF-…).</li>
 * </ul>
 * Même classe dans chaque service (pas de bibliothèque partagée : chaque service se construit seul).
 */
public final class Correlation {

    public static final String ENTETE = "X-Correlation-Id";
    public static final String CLE_MDC = "correlationId";
    /** Valeur reçue : courte et sans caractères de contrôle (pas d'injection dans les journaux). Même règle qu'à la gateway. */
    private static final Pattern VALEUR_ACCEPTEE = Pattern.compile("[A-Za-z0-9._:-]{1,64}");

    private Correlation() {
    }

    /** La valeur reçue si elle est valide, sinon un nouvel identifiant (UUID). */
    public static String accepterOuGenerer(String recu) {
        return recu != null && VALEUR_ACCEPTEE.matcher(recu).matches() ? recu : UUID.randomUUID().toString();
    }

    /** Identifiant du traitement en cours sur ce thread, ou null. */
    public static String courant() {
        return MDC.get(CLE_MDC);
    }

    /**
     * La tâche, exécutée avec le MDC du thread qui l'a créée (le MDC est lié au thread : sans cela, une tâche
     * confiée à un autre thread perdrait l'identifiant). Le MDC du thread d'exécution est rétabli ensuite.
     */
    public static Runnable avecContexte(Runnable tache) {
        Map<String, String> contexte = MDC.getCopyOfContextMap();
        return () -> {
            Map<String, String> avant = MDC.getCopyOfContextMap();
            remplacer(contexte);
            try {
                tache.run();
            } finally {
                remplacer(avant);
            }
        };
    }

    private static void remplacer(Map<String, String> contexte) {
        if (contexte == null) {
            MDC.clear();
        } else {
            MDC.setContextMap(contexte);
        }
    }
}
